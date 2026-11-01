package net.kdt.pojavlaunch.multirt;

import android.app.Instrumentation;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.platform.app.InstrumentationRegistry;

import net.kdt.pojavlaunch.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuntimeManagerLayoutTest {
    @Test public void installedRuntimesAreNotClippedByTheOuterScrollContainer() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            ContextThemeWrapper context = new ContextThemeWrapper(instrumentation.getTargetContext(),
                    androidx.appcompat.R.style.Theme_AppCompat);
            int[][] sizes = {{800, 360}, {360, 800}, {1280, 800}};
            for (int[] size : sizes) {
                View root = LayoutInflater.from(context).inflate(R.layout.fragment_runtime_manager, null);
                RecyclerView list = root.findViewById(R.id.runtime_recycler);
                list.setLayoutManager(new LinearLayoutManager(context));
                list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    final int[] versions = {8, 17, 21, 25};

                    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                        return new RecyclerView.ViewHolder(LayoutInflater.from(context)
                                .inflate(R.layout.item_multirt_runtime, parent, false)) {};
                    }

                    @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                        ((TextView) holder.itemView.findViewById(R.id.multirt_view_java_version))
                                .setText("Java " + versions[position]);
                    }

                    @Override public int getItemCount() { return versions.length; }
                });
                float density = context.getResources().getDisplayMetrics().density;
                int width = Math.round(size[0] * density);
                int height = Math.round(size[1] * density);
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, width, height);

                assertEquals("All installed runtimes must be laid out at " + size[0] + "x" + size[1],
                        4, list.getChildCount());
                assertFalse("Only the outer container should scroll", list.canScrollVertically(1));
                View last = list.getLayoutManager().findViewByPosition(3);
                assertTrue("Java 25 must fit inside the installed list", last.getBottom() <= list.getHeight());
            }
        });
    }
}
