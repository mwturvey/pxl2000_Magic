import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
	// write your code here

        String fileBase = "Test20";
        //String fileBase = "Christmas_88";
        //String fileBase = "pxl2000_192khtz_section";

        String filename = fileBase + ".wav";
        pxlConverter converter = new pxlConverter(fileBase + ".wav");

        Map<String, String> config = new HashMap<>();

        File dir = new File(fileBase);
        dir.mkdirs();

        //config.put("fft_csv","true");
        config.put("fft_channels", "13,14,15,16");
        config.put("directory", fileBase);
        int videoChannel = 1;
        try{
            //converter.createNonRepeatingWav();
            //converter.copyNonRepeatingWav();

            converter.fftPass( config, filename,"fft.wav", videoChannel);
            converter.rollingAverage( config,"fft.wav", "smoothed_fft.wav");
            converter.findSyncs( config,"smoothed_fft.wav", "syncs.csv");
            converter.findFrames( config,"syncs.csv", "raw_frames.csv");
            converter.drawFrames( config,"raw_frames.csv" , fileBase + ".wav" , "image", videoChannel);
            converter.findValidSoundLocations(config, "raw_frames.csv", "audio_locations.csv");
            converter.generateFinalAudio(config, filename, "audio_locations.csv", "final_audio.wav", videoChannel);
        }
        catch (Exception e)
        {
            int a = 0;
        }
    }
}
