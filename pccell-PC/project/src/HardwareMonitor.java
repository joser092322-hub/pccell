import java.io.File;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public class HardwareMonitor {

    public static class SystemStats {
        public int cpuPercent;
        public int ramPercent;
        public long ramFreeMb;
        public long ramTotalMb;
        public int diskPercent;
        public long diskFreeGb;
        public long diskTotalGb;
    }

    public static SystemStats getStats() {
        SystemStats stats = new SystemStats();
        try {
            OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double load = os.getCpuLoad();
            stats.cpuPercent = (load < 0) ? 0 : (int) Math.round(load * 100);

            long totalMem = os.getTotalMemorySize();
            long freeMem = os.getFreeMemorySize();
            stats.ramTotalMb = totalMem / (1024 * 1024);
            stats.ramFreeMb = freeMem / (1024 * 1024);
            stats.ramPercent = totalMem > 0 ? (int) ((totalMem - freeMem) * 100 / totalMem) : 0;

            File cDrive = new File("C:\\");
            long totalDisk = cDrive.getTotalSpace();
            long freeDisk = cDrive.getFreeSpace();
            stats.diskTotalGb = totalDisk / (1024 * 1024 * 1024);
            stats.diskFreeGb = freeDisk / (1024 * 1024 * 1024);
            stats.diskPercent = totalDisk > 0 ? (int) ((totalDisk - freeDisk) * 100 / totalDisk) : 0;
        } catch (Exception e) {
            System.err.println("Erro ao ler status de hardware: " + e.getMessage());
        }
        return stats;
    }
}
