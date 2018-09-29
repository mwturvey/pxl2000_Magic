import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
	// write your code here

        //String fileBase = "Test18";
        String fileBase = "Mikes_B_day_89_No10";
        //String fileBase = "pxl2000_192khtz_section";

        pxlConverter converter = new pxlConverter(fileBase + ".wav");

        Map<String, String> config = new HashMap<>();

        config.put("reuse_fft","true");
        int videoChannel = 1;
        try{
            //converter.createNonRepeatingWav();
            //converter.copyNonRepeatingWav();
            //converter.fftPass( config, null, videoChannel);
            converter.rollingAverage( config,fileBase + ".fft.wav", fileBase + ".smoothed_fft.wav");
            converter.findSyncs( config,fileBase + ".smoothed_fft.wav", fileBase + ".syncs.csv");
            converter.findFrames( config,fileBase + ".syncs.csv", fileBase + ".raw_frames.csv");
            converter.drawFrames( config,fileBase + ".raw_frames.csv" , fileBase + ".wav" , fileBase + ".image", videoChannel);

        }
        catch (Exception e)
        {
            int a = 0;
        }
    }
}
