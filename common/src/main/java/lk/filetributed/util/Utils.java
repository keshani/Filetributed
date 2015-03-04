package lk.filetributed.util;

public class Utils {
    public static int getClusterID(String ipAddress, int port,int no_clusters){
        int result = ipAddress.hashCode();
        result = 31 * result + port;
        int clusterID = Math.abs(result%no_clusters);
        return clusterID;
    }

}
