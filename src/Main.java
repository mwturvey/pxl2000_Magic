public class Main {

    public static void main(String[] args) {
	// write your code here

        String fileBase = "Test13";

        pxlConverter converter = new pxlConverter(fileBase + ".wav");

        try{
            converter.fftPass(null);
            converter.rollingAverage(fileBase + ".fft.wav", fileBase + ".smoothed_fft.wav");
        }
        catch (Exception e)
        {
            int a = 0;
        }
    }
}
