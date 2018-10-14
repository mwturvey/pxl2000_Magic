import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception{

        if (args.length == 0) {

            System.out.println("No Args Found!");
            System.out.println("Usage: java  -jar PxlMagic.jar [VideoFile.Wav]");
            System.out.println();
            System.out.println(
                    "[VideoFile.Wav] must be an uncompressed WAV file recorded at 192000 samples per second");
            return;
        }
        System.out.println("Args are: ");
        for (String arg: args) {
            System.out.println("    " + arg );

        }
	// write your code here

        //String fileBase = "9_30_2018";
//      //  String fileBase = "Test24";
        //String fileBase = "Christmas_88";
        String fileBase = "pxl2000_192khtz_section";

        fileBase = args[0].split("\\.")[0];

        String filename = fileBase + ".wav";
        pxlConverter converter = new pxlConverter(fileBase + ".wav");

        Map<String, String> config = new HashMap<>();

        File dir = new File(fileBase);
        System.out.println("Making Directory " + fileBase);
        dir.mkdirs();

        config.put("fft_csv","true");
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
            converter.drawFrames( config,"raw_frames.csv" , filename , "image", videoChannel);
            converter.findValidSoundLocations(config, "raw_frames.csv", "audio_locations.csv");
            converter.generateFinalAudio(config, filename, "audio_locations.csv", "final_audio.wav", videoChannel);
        }
        catch (Exception e)
        {
            // for now, re-throw the exception so we can better debug the issue
            throw(e);
        }
    }
}
