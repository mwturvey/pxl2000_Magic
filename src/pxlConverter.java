import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.util.Arrays;

public class pxlConverter {

    private String filename;
    private static final int imageBuffLen = 128;

    pxlConverter(String file)
    {
        filename = file;

    }


    public void fftPass(String fileIn) throws Exception
    {
        if (null == fileIn){
            fileIn = filename;
        }

        // In the first pass, we'll do the FFT of the input file.
        String firstPassFilename = fileIn.replaceAll("\\.wav",".fft.wav");

        WavFile fftWav = WavFile.newWavFile(new File(firstPassFilename),1,150000 * 60 * 60 * 2 ,16,192000);

        //fftWav.openFile();
        //fftWav.setChannels(1);
        //fftWav.writeHeader();

        AudioInputStream audioInStream = AudioSystem.getAudioInputStream(new File(fileIn));

        AudioFormat format = audioInStream.getFormat();
        // make sure the format is correct (16-bit, stereo, little endian)


        int frameSize = format.getFrameSize();
        float sampleRate = format.getSampleRate();

        if (192000 != sampleRate)
        {
            throw new Exception("Unsupported Sample Rate.  Must be 192000");
        }
        if (format.getChannels() != 2)
        {
            throw new Exception("Stereo Encoding Required");
        }
        if(format.getSampleSizeInBits() != 16)
        {
            throw new Exception("Must be 16-bit samples");
        }


        double[] imageBuff = new double[imageBuffLen];

        int bytesRead = 0;


        int circularBuffIndex = 0;
        int iterations = 0;


        while (bytesRead >=0) {

            if (true || iterations >= imageBuffLen) {
                double[] realBuffer = new double[imageBuffLen];
                // copy the buffer into a new one for use with the FFT
                for (int i = 0; i < imageBuffLen; i++) {
                    int index = (i + circularBuffIndex + imageBuffLen) % imageBuffLen;
                    realBuffer[i] = imageBuff[index];
                }

                // create a new buffer for the imaginary output
                double[] imaginaryBuffer = new double[imageBuffLen];
                Arrays.fill(imaginaryBuffer, (double) (0));

                // run the fft
                Fft.transform(realBuffer, imaginaryBuffer);

                int[] sampleOut= new int[1];
                int imageBuffIndex = (circularBuffIndex-1 + imageBuffLen) % imageBuffLen;
                sampleOut[0] = (int)imageBuff[(circularBuffIndex-1 + imageBuffLen) % imageBuffLen];
                double foo = realBuffer[imageBuffLen-1];
                double foo2 = realBuffer[imageBuffLen-2];
                int indexToSample = 9;
                sampleOut[0] = (int)Math.sqrt(realBuffer[5] * realBuffer[5] + imaginaryBuffer[5] * imaginaryBuffer[5]);
                sampleOut[0] = (int)Math.sqrt(realBuffer[indexToSample] * realBuffer[indexToSample] + imaginaryBuffer[indexToSample] * imaginaryBuffer[indexToSample]);
                // normalize
                sampleOut[0] = sampleOut[0] / 8;
                if (sampleOut[0] > 30000) {
                    sampleOut[0] = 30000;
                }
                fftWav.writeFrames(sampleOut,0, 1);
                // output the results to a wav file
                // output the results to a csv file
            }
            byte[] frame = new byte[4];
            bytesRead = audioInStream.read(frame);

            if (bytesRead < 0)
            {
                break;
            }

            int cbuffIndex = circularBuffIndex % imageBuffLen;
            //double val =  (int)(frame[0]&0xff) + (int)(frame[1] <<8);
            double val =  (int)(frame[1] * 0x100 + (frame[0] + 256)%256);
            imageBuff[cbuffIndex] = val;

            circularBuffIndex++;
            iterations++;

        }


        fftWav.close();
        // close the wave file
        // close the csv file
    }

    public void rollingAverage(String filenameIn, String filenameOut) throws Exception
    {
        WavFile wavIn = WavFile.openWavFile(new File(filenameIn));

        int numChannels = wavIn.getNumChannels();

        // Create a buffer of 100 frames
        int[] buffer = new int[100 * numChannels];

        int framesRead;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        do
        {
            // Read frames into buffer
            framesRead = wavIn.readFrames(buffer, 100);

            // Loop through frames and look for minimum and maximum value
            for (int s=0 ; s<framesRead * numChannels ; s++)
            {
                if (buffer[s] > max) max = buffer[s];
                if (buffer[s] < min) min = buffer[s];
            }
        }
        while (framesRead != 0);

        // Close the wavFile
        wavIn.close();

    }
   // public void

}
