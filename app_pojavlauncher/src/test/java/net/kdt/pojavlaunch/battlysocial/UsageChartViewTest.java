package net.kdt.pojavlaunch.battlysocial;

import org.junit.Test;
import static org.junit.Assert.*;

public class UsageChartViewTest {
    @Test public void scaleHasThreeLegibleIntervalsAndIncludesPeak() {
        assertEquals(180, UsageChartView.axisMaximum(0));
        assertEquals(360, UsageChartView.axisMaximum(181));
        assertEquals(5400, UsageChartView.axisMaximum(5400));
        assertEquals(3600, UsageChartView.axisMaximum(3000));
        assertEquals(21600, UsageChartView.axisMaximum(12000));
    }
    @Test public void tapsSelectNearestDateOrHourlyBarAndClampEdges() {
        assertEquals(-1, UsageChartView.indexAt(0, 10, 100, 0, false));
        assertEquals(0, UsageChartView.indexAt(-20, 10, 100, 90, false));
        assertEquals(89, UsageChartView.indexAt(110, 10, 100, 90, false));
        assertEquals(12, UsageChartView.indexAt(50, 0, 100, 24, true));
        assertEquals(3, UsageChartView.indexAt(50, 0, 100, 7, false));
    }
}
