public class Main {

    public static void main(String[] args) {
	// write your code here

        String fileBase = "Test14";
        //String fileBase = "pxl2000_192khtz_section_swapped";

        pxlConverter converter = new pxlConverter(fileBase + ".wav");

        try{
            //converter.createNonRepeatingWav();
            //converter.copyNonRepeatingWav();
            //converter.fftPass(null);
            //converter.rollingAverage(fileBase + ".fft.wav", fileBase + ".smoothed_fft.wav");
            converter.findSyncs(fileBase + ".smoothed_fft.wav", fileBase + ".syncs.csv");
            converter.findFrames(fileBase + ".syncs.csv", fileBase + ".raw_frames.csv");
            converter.drawFrames(fileBase + ".raw_frames.csv" , fileBase + ".wav" , fileBase + ".image", 0);

        }
        catch (Exception e)
        {
            int a = 0;
        }
    }
}
