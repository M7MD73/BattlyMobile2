package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.text.InputFilter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class BattlyWorldsModeration {
    private static final String BASE = "https://api.battlylauncher.com/api/v2/battlyworlds";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String lastNotice = "";
    private static boolean monitoring;
    private static final java.util.concurrent.atomic.AtomicLong ACCESS_REVISION = new java.util.concurrent.atomic.AtomicLong();

    static void invalidatePendingAuthorizations() {
        ACCESS_REVISION.incrementAndGet();
    }

    static void authorize(Activity activity, String scope, Runnable allowed) {
        if (activity == null || activity.isFinishing()) return;
        final String token = BattlyWorldsInvites.getBattlyToken(activity);
        final long revision = ACCESS_REVISION.get();
        new Thread(() -> {
            try {
                JsonObject response = status(token);
                JsonObject restriction = BattlyWorldsRestriction.find(response, scope);
                MAIN.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || revision != ACCESS_REVISION.get()
                            || !token.equals(BattlyWorldsInvites.getBattlyToken(activity))) return;
                    if (restriction == null) allowed.run();
                    else notice(activity, reason(restriction));
                });
            } catch (Exception error) {
                MAIN.post(() -> Toast.makeText(activity, R.string.battlyworlds_moderation_unavailable, Toast.LENGTH_LONG).show());
            }
        }, "Worlds-access-check").start();
    }

    private static JsonObject status(String token) throws Exception {
        if (token.isEmpty()) throw new IllegalStateException("Battly account required");
        JsonObject response = BattlyWorldsInvites.getAuthorizedJson(BASE + "/moderation/me", token);
        if (response == null || !response.has("restrictions") || !response.get("restrictions").isJsonArray()) throw new IllegalStateException("Access check unavailable");
        return response;
    }

    private static String reason(JsonObject row) {
        String reason = row.has("reason") && row.get("reason").isJsonPrimitive() ? row.get("reason").getAsString() : "";
        if (reason.length() > 1000) reason = reason.substring(0,1000);
        String end = row.has("expiresAt") && row.get("expiresAt").isJsonPrimitive() ? row.get("expiresAt").getAsString() : "";
        return reason + (end.isEmpty() ? "" : " · " + end);
    }

    static void receive(Activity activity, JSONObject payload) {
        if (activity == null) return;
        try {
            JsonObject row = Tools.GLOBAL_GSON.fromJson(payload.toString(), JsonObject.class);
            JsonObject response = new JsonObject(); JsonArray rows = new JsonArray(); rows.add(row); response.add("restrictions",rows);
            apply(activity,response);
        } catch (Exception ignored) { }
    }

    private static void apply(Activity activity, JsonObject response) {
        if(activity.isFinishing() || activity.isDestroyed())return;
        JsonObject room = BattlyWorldsRestriction.find(response, BattlyWorldsManager.isHosting() ? "host" : "join");
        JsonObject voice = BattlyWorldsRestriction.find(response, "voice");
        if (voice != null) {
            invalidatePendingAuthorizations();
            BattlyWorldsVoiceManager.leave();
        }
        if (room != null) {
            BattlyWorldsManager.setWaiting(activity, true);
            BattlyWorldsInvites.stopPublicRoomHeartbeat();
            BattlyWorldsPreferences.clearActiveRoomCode(activity);
        }
        JsonObject restriction = room != null ? room : voice;
        String message = restriction == null ? "" : reason(restriction);
        if (restriction != null && message.isEmpty()) message = activity.getString(R.string.battlyworlds_restricted_title);
        if (!message.isEmpty() && !message.equals(lastNotice)) { lastNotice=message;notice(activity,message); }
        if(message.isEmpty()) lastNotice="";
    }

    static void startMonitoring() {
        if(monitoring)return;monitoring=true;
        MAIN.postDelayed(new Runnable() {
            @Override public void run() {
                Activity activity=BattlyWorldsManager.getAttachedActivity();
                if(activity!=null && !activity.isFinishing() && BattlyWorldsManager.isRoomActive()) {
                    String token=BattlyWorldsInvites.getBattlyToken(activity);
                    new Thread(()->{
                        try { JsonObject response=status(token);MAIN.post(()->{if(!activity.isFinishing() && token.equals(BattlyWorldsInvites.getBattlyToken(activity)))apply(activity,response);}); }
                        catch(Exception ignored) { /* A network outage is not a moderation decision. */ }
                    },"Worlds-moderation-check").start();
                }
                MAIN.postDelayed(this,15000);
            }
        },15000);
    }

    private static void notice(Activity activity,String reason) {
        Tools.createStyledDialogBuilder(activity).setTitle(R.string.battlyworlds_restricted_title)
            .setMessage(reason.isEmpty() ? activity.getString(R.string.battlyworlds_restricted_title) : reason)
            .setPositiveButton(android.R.string.ok,null).show();
    }

    static void chooseReport(Activity activity) {
        List<BattlyWorldsRealtimeClient.Member> members=BattlyWorldsRealtimeClient.getMembers();
        List<String> labels=new ArrayList<>();List<BattlyWorldsRealtimeClient.Member> targets=new ArrayList<>();
        labels.add(activity.getString(R.string.battlyworlds_report_room));targets.add(null);
        for(BattlyWorldsRealtimeClient.Member member:members)if(!member.userId.equals(BattlyWorldsRealtimeClient.getCurrentUserId())){labels.add(member.username);targets.add(member);}
        Tools.createStyledDialogBuilder(activity).setTitle(R.string.battlyworlds_report)
            .setItems(labels.toArray(new String[0]),(dialog,index)->showReport(activity,targets.get(index))).show();
    }

    static void showReport(Activity activity, BattlyWorldsRealtimeClient.Member target) {
        final String code=BattlyWorldsRealtimeClient.getRoomCode();
        final String token=BattlyWorldsInvites.getBattlyToken(activity);
        if(code.isEmpty() || token.isEmpty())return;
        LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);int padding=(int)(20*activity.getResources().getDisplayMetrics().density);form.setPadding(padding,padding,padding,padding);
        Spinner category=new Spinner(activity);category.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,activity.getResources().getStringArray(R.array.battlyworlds_report_categories)));
        EditText details=new EditText(activity);details.setHint(R.string.battlyworlds_report_description);details.setMinLines(4);details.setMaxLines(7);details.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});details.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        android.widget.TextView privacy=new android.widget.TextView(activity);privacy.setText(R.string.battlyworlds_report_privacy);
        form.addView(category);form.addView(details);form.addView(privacy);
        final androidx.appcompat.app.AlertDialog dialog=Tools.createStyledDialogBuilder(activity).setTitle(activity.getString(R.string.battlyworlds_report)+(target==null?"":" · "+target.username)).setView(form)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.battlyworlds_report_send,null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(button->{
            String description=details.getText().toString().trim();if(description.length()<10){details.setError(activity.getString(R.string.battlyworlds_report_minimum));return;}
            dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).setEnabled(false);dialog.setCancelable(false);
            final String[] keys={"harassment","hate","cheating","inappropriate","privacy","other"};
            final String categoryId=keys[category.getSelectedItemPosition()];
            new Thread(()->{
                try{
                    JSONObject body=new JSONObject();body.put("roomCode",code);body.put("category",categoryId);body.put("description",description);body.put("platform","mobile");if(target!=null)body.put("targetUserId",target.userId);
                    JsonObject result=BattlyWorldsInvites.postJson(BASE+"/reports",token,body);
                    if(!result.has("id"))throw new IllegalStateException(result.has("message")?result.get("message").getAsString():activity.getString(R.string.battlyworlds_report_failed));
                    MAIN.post(()->{if(activity.isFinishing())return;dialog.dismiss();Toast.makeText(activity,activity.getString(R.string.battlyworlds_report_sent)+" "+result.get("id").getAsString(),Toast.LENGTH_LONG).show();});
                }catch(Exception error){MAIN.post(()->{if(activity.isFinishing())return;dialog.getButton(-1).setEnabled(true);dialog.getButton(-2).setEnabled(true);dialog.setCancelable(true);details.setError(error.getMessage());});}
            },"Worlds-report").start();
        }));dialog.show();
    }
}
