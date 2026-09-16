package net.kdt.patch.audio;

import org.lwjgl.glfw.CallbackBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.spi.MixerProvider;

/** JavaSound capture devices backed by Android AudioRecord input routes. */
public final class AndroidCaptureMixerProvider extends MixerProvider {
    static final String MIXER_NAME = "Battly Android microphone";
    private static volatile List<DeviceDescriptor> devices = discoverDevices();

    public AndroidCaptureMixerProvider() {
        System.out.println("[BattlyAudio] JavaSound Android microphone provider loaded");
    }

    @Override
    public Mixer.Info[] getMixerInfo() {
        refreshDevicesIfNeeded();
        List<DeviceDescriptor> current = devices;
        Mixer.Info[] infos = new Mixer.Info[current.size()];
        for (int index = 0; index < current.size(); index++) infos[index] = current.get(index).info;
        return infos;
    }

    @Override
    public Mixer getMixer(Mixer.Info info) {
        refreshDevicesIfNeeded();
        List<DeviceDescriptor> current = devices;
        if (info == null && !current.isEmpty()) return current.get(0).mixer;
        for (DeviceDescriptor device : current) {
            if (device.info == info || (info != null
                    && device.info.getName().equals(info.getName())
                    && "Battly".equals(info.getVendor()))) {
                return device.mixer;
            }
        }
        throw new IllegalArgumentException("Unknown mixer: " + info);
    }

    static boolean isAndroidMixer(Mixer.Info info) {
        return info != null && "Battly".equals(info.getVendor())
                && info.getDescription().startsWith("Android microphone capture");
    }

    private static final class CaptureMixerInfo extends Mixer.Info {
        CaptureMixerInfo(String name, int deviceId) {
            super(name, "Battly", "Android microphone capture device " + deviceId, "2.0");
        }
    }

    private static final class DeviceDescriptor {
        final int deviceId;
        final CaptureMixerInfo info;
        final AndroidCaptureMixer mixer;

        DeviceDescriptor(int deviceId, String name) {
            this.deviceId = deviceId;
            this.info = new CaptureMixerInfo(name, deviceId);
            this.mixer = new AndroidCaptureMixer(info, deviceId);
        }
    }

    private static List<DeviceDescriptor> discoverDevices() {
        List<DeviceDescriptor> devices = new ArrayList<>();
        try {
            String encoded = System.getenv("BATTLY_AUDIO_INPUTS");
            if (encoded == null || encoded.trim().isEmpty()) {
                encoded = CallbackBridge.nativeListAndroidAudioInputs();
            }
            if (encoded != null && !encoded.trim().isEmpty()) {
                for (String row : encoded.split("\\n")) {
                    String[] fields = row.split("\\t", 3);
                    if (fields.length < 3) continue;
                    try {
                        int id = Integer.parseInt(fields[0]);
                        String name = fields[2].trim();
                        if (name.isEmpty()) name = "Android microphone " + id;
                        devices.add(new DeviceDescriptor(id, "Battly - " + name));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Throwable error) {
            System.out.println("[BattlyAudio] Android input enumeration unavailable: " + error);
        }
        if (devices.isEmpty()) devices.add(new DeviceDescriptor(-1, MIXER_NAME));
        System.out.println("[BattlyAudio] Exposing " + devices.size() + " Android microphone input(s)");
        return Collections.unmodifiableList(devices);
    }

    private static synchronized void refreshDevicesIfNeeded() {
        List<DeviceDescriptor> current = devices;
        if (current.size() != 1 || current.get(0).deviceId >= 0) return;
        List<DeviceDescriptor> refreshed = discoverDevices();
        if (refreshed.size() != 1 || refreshed.get(0).deviceId >= 0) devices = refreshed;
    }

    private static final class AndroidCaptureMixer implements Mixer {
        private static final AudioFormat[] FORMATS = {
                new AudioFormat(8000f, 16, 1, true, false),
                new AudioFormat(16000f, 16, 1, true, false),
                new AudioFormat(24000f, 16, 1, true, false),
                new AudioFormat(44100f, 16, 1, true, false),
                new AudioFormat(48000f, 16, 1, true, false),
                new AudioFormat(44100f, 16, 2, true, false),
                new AudioFormat(48000f, 16, 2, true, false)
        };
        private static final DataLine.Info TARGET_INFO =
                new DataLine.Info(TargetDataLine.class, FORMATS,
                        AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED);
        private final List<LineListener> listeners = new CopyOnWriteArrayList<>();
        private final CaptureMixerInfo info;
        private final int deviceId;
        private volatile boolean open;

        AndroidCaptureMixer(CaptureMixerInfo info, int deviceId) {
            this.info = info;
            this.deviceId = deviceId;
        }

        @Override public Info getMixerInfo() { return info; }
        @Override public Line.Info getLineInfo() { return new Line.Info(Mixer.class); }
        @Override public Line.Info[] getSourceLineInfo() { return new Line.Info[0]; }
        @Override public Line.Info[] getSourceLineInfo(Line.Info info) { return new Line.Info[0]; }
        @Override public Line.Info[] getTargetLineInfo() { return new Line.Info[]{TARGET_INFO}; }
        @Override public Line.Info[] getTargetLineInfo(Line.Info info) {
            return isLineSupported(info) ? new Line.Info[]{TARGET_INFO} : new Line.Info[0];
        }
        @Override public boolean isLineSupported(Line.Info info) {
            if (!(info instanceof DataLine.Info)) return false;
            DataLine.Info dataInfo = (DataLine.Info) info;
            Class<?> lineClass = dataInfo.getLineClass();
            boolean lineTypeSupported = lineClass.isAssignableFrom(TargetDataLine.class)
                    || TargetDataLine.class.isAssignableFrom(lineClass);
            AudioFormat[] requested = dataInfo.getFormats();
            boolean formatSupported = requested.length == 0;
            for (AudioFormat format : requested) {
                if (AndroidTargetDataLine.isSupported(format)) {
                    formatSupported = true;
                    break;
                }
            }
            return lineTypeSupported && formatSupported;
        }
        @Override public Line getLine(Line.Info info) throws LineUnavailableException {
            if (!isLineSupported(info)) throw new IllegalArgumentException("Unsupported line: " + info);
            AudioFormat selected = FORMATS[1];
            AudioFormat[] requested = ((DataLine.Info) info).getFormats();
            for (AudioFormat format : requested) {
                if (AndroidTargetDataLine.isSupported(format)) {
                    selected = format;
                    break;
                }
            }
            return new AndroidTargetDataLine(this, selected, deviceId);
        }
        @Override public int getMaxLines(Line.Info info) {
            return isLineSupported(info) ? AudioSystem.NOT_SPECIFIED : 0;
        }
        @Override public Line[] getSourceLines() { return new Line[0]; }
        @Override public Line[] getTargetLines() { return new Line[0]; }
        @Override public void synchronize(Line[] lines, boolean maintainSync) {
            throw new IllegalArgumentException("Synchronization is not supported");
        }
        @Override public void unsynchronize(Line[] lines) {
            throw new IllegalArgumentException("Synchronization is not supported");
        }
        @Override public boolean isSynchronizationSupported(Line[] lines, boolean maintainSync) { return false; }
        @Override public void open() {
            if (!open) {
                open = true;
                emit(LineEvent.Type.OPEN);
            }
        }
        @Override public void close() {
            if (open) {
                open = false;
                emit(LineEvent.Type.CLOSE);
            }
        }
        @Override public boolean isOpen() { return open; }
        @Override public Control[] getControls() { return new Control[0]; }
        @Override public boolean isControlSupported(Control.Type control) { return false; }
        @Override public Control getControl(Control.Type control) {
            throw new IllegalArgumentException("Unsupported control: " + control);
        }
        @Override public void addLineListener(LineListener listener) { if (listener != null) listeners.add(listener); }
        @Override public void removeLineListener(LineListener listener) { listeners.remove(listener); }
        private void emit(LineEvent.Type type) {
            LineEvent event = new LineEvent(this, type, AudioSystem.NOT_SPECIFIED);
            for (LineListener listener : listeners) listener.update(event);
        }
    }

    private static final class AndroidTargetDataLine implements TargetDataLine {
        private static final AudioFormat DEFAULT_FORMAT = new AudioFormat(16000f, 16, 1, true, false);
        private final AndroidCaptureMixer mixer;
        private final List<LineListener> listeners = new CopyOnWriteArrayList<>();
        private AudioFormat format;
        private final int deviceId;
        private int handle = -1;
        private int bufferSize;
        private long framesRead;
        private volatile boolean open;
        private volatile boolean started;

        AndroidTargetDataLine(AndroidCaptureMixer mixer, AudioFormat format, int deviceId) {
            this.mixer = mixer;
            this.format = format;
            this.deviceId = deviceId;
        }

        static boolean isSupported(AudioFormat format) {
            return format != null
                    && AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                    && format.getSampleSizeInBits() == 16
                    && (format.getChannels() == 1 || format.getChannels() == 2)
                    && !format.isBigEndian()
                    && format.getSampleRate() >= 8000f
                    && format.getSampleRate() <= 48000f;
        }

        @Override public synchronized void open() throws LineUnavailableException { open(DEFAULT_FORMAT); }
        @Override public synchronized void open(AudioFormat requested) throws LineUnavailableException {
            int defaultBuffer = Math.max(requested.getFrameSize() * 1024,
                    Math.round(requested.getFrameRate() * requested.getFrameSize() / 2f));
            open(requested, defaultBuffer);
        }
        @Override public synchronized void open(AudioFormat requested, int requestedBufferSize)
                throws LineUnavailableException {
            if (open) return;
            if (!isSupported(requested)) throw new IllegalArgumentException("Unsupported capture format: " + requested);
            try {
                int frameSize = requested.getFrameSize();
                bufferSize = Math.max(frameSize, requestedBufferSize - requestedBufferSize % frameSize);
                handle = CallbackBridge.nativeOpenAndroidAudioInput(deviceId,
                        Math.round(requested.getSampleRate()), requested.getChannels(), bufferSize);
                if (handle < 0) throw new LineUnavailableException(
                        "Android could not open microphone device " + deviceId);
                format = requested;
                framesRead = 0;
                open = true;
                mixer.open();
                emit(LineEvent.Type.OPEN);
            } catch (LineUnavailableException error) {
                throw error;
            } catch (Throwable error) {
                LineUnavailableException wrapped = new LineUnavailableException(
                        "Unable to initialize Android microphone: " + error.getMessage());
                wrapped.initCause(error);
                throw wrapped;
            }
        }
        @Override public synchronized void start() {
            if (!open || started) return;
            started = CallbackBridge.nativeStartAndroidAudioInput(handle);
            if (!started) return;
            emit(LineEvent.Type.START);
        }
        @Override public synchronized void stop() {
            if (!open || !started) return;
            started = false;
            CallbackBridge.nativeStopAndroidAudioInput(handle);
            emit(LineEvent.Type.STOP);
        }
        @Override public int read(byte[] data, int offset, int length) {
            if (data == null) throw new NullPointerException("data");
            if (offset < 0 || length < 0 || offset + length > data.length) throw new ArrayIndexOutOfBoundsException();
            AudioFormat current = format;
            int frameSize = current.getFrameSize();
            int requestedFrames = length / frameSize;
            if (requestedFrames <= 0) return 0;
            int copiedBytes = 0;
            int requestedBytes = requestedFrames * frameSize;
            while (copiedBytes < requestedBytes && open) {
                if (!started) {
                    sleepBriefly();
                    continue;
                }
                int read = CallbackBridge.nativeReadAndroidAudioInput(handle, data,
                        offset + copiedBytes, requestedBytes - copiedBytes);
                if (read <= 0) {
                    sleepBriefly();
                    continue;
                }
                copiedBytes += read;
                framesRead += read / frameSize;
            }
            return copiedBytes;
        }
        private static void sleepBriefly() {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        @Override public synchronized void close() {
            if (!open) return;
            stop();
            CallbackBridge.nativeCloseAndroidAudioInput(handle);
            handle = -1;
            open = false;
            emit(LineEvent.Type.CLOSE);
        }
        @Override public int available() {
            if (!open) return 0;
            return 0;
        }
        @Override public void drain() { }
        @Override public void flush() {
            // AudioRecord has no queued application-side buffer to discard here.
        }
        @Override public AudioFormat getFormat() { return format; }
        @Override public int getBufferSize() { return bufferSize; }
        @Override public int getFramePosition() { return (int) Math.min(Integer.MAX_VALUE, framesRead); }
        @Override public long getLongFramePosition() { return framesRead; }
        @Override public long getMicrosecondPosition() {
            return format.getFrameRate() <= 0f ? 0L : (long) (framesRead * 1_000_000d / format.getFrameRate());
        }
        @Override public float getLevel() { return AudioSystem.NOT_SPECIFIED; }
        @Override public boolean isRunning() { return started; }
        @Override public boolean isActive() { return started; }
        @Override public boolean isOpen() { return open; }
        @Override public Line.Info getLineInfo() { return new DataLine.Info(TargetDataLine.class, format); }
        @Override public Control[] getControls() { return new Control[0]; }
        @Override public boolean isControlSupported(Control.Type control) { return false; }
        @Override public Control getControl(Control.Type control) {
            throw new IllegalArgumentException("Unsupported control: " + control);
        }
        @Override public void addLineListener(LineListener listener) { if (listener != null) listeners.add(listener); }
        @Override public void removeLineListener(LineListener listener) { listeners.remove(listener); }
        private void emit(LineEvent.Type type) {
            LineEvent event = new LineEvent(this, type, framesRead);
            for (LineListener listener : listeners) listener.update(event);
        }
    }
}
