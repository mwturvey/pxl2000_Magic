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
//                sampleOut[0] = (int)Math.sqrt(realBuffer[5] * realBuffer[5] + imaginaryBuffer[5] * imaginaryBuffer[5]);
//                sampleOut[0] = (int)Math.sqrt(realBuffer[indexToSample] * realBuffer[indexToSample] + imaginaryBuffer[indexToSample] * imaginaryBuffer[indexToSample]);
                sampleOut[0] = (int)Math.sqrt(realBuffer[9] * realBuffer[9] + imaginaryBuffer[9] * imaginaryBuffer[9]) +
                               (int)Math.sqrt(realBuffer[10] * realBuffer[10] + imaginaryBuffer[10] * imaginaryBuffer[10]) ;
                // normalize
                sampleOut[0] = sampleOut[0] / 16;
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
        WavFile wavOut = WavFile.newWavFile(new File(filenameOut),1,3 ,16,192000);

        int numChannels = wavIn.getNumChannels();
        if (numChannels != 1)
        {
            throw new Exception("Mono Encoding Required");
        }

        // Create a buffer of 1 frame
        int[] buffer = new int[1 * numChannels];

        int avgSize = 100;

        int[] avgBuffer = new int[avgSize];

        int framesRead;
        int frameCounter = 0;

        do
        {
            // Read frames into buffer
            framesRead = wavIn.readFrames(buffer, 1);

            if (framesRead == 0){
                break;
            }
            if (0 == frameCounter)
            {
                Arrays.fill(avgBuffer, 0);
            }

            avgBuffer[frameCounter % avgSize] = buffer[0];
            frameCounter++;

            int sum=0;
            for (int i=0; i < avgBuffer.length; i++)
            {
                sum += avgBuffer[i];
            }

            int avg = sum / avgBuffer.length;



            int[] sampleOut= new int[1];

            sampleOut[0] = avg;

            if (sampleOut[0] > 30000) {
                sampleOut[0] = 30000;
            }
            wavOut.writeFrames(sampleOut,0, 1);

        }
        while (framesRead != 0);

        // Close the wavFile
        wavIn.close();
        wavOut.close();

    }

    public void findSyncs(String filenameIn, String filenameOut) throws Exception
    {
        WavFile wavIn = WavFile.openWavFile(new File(filenameIn));
        //WavFile wavOut = WavFile.newWavFile(new File(filenameOut),1,3 ,16,192000);
        FileWriter fileOut = new FileWriter(filenameOut);
        int numChannels = wavIn.getNumChannels();
        if (numChannels != 1)
        {
            throw new Exception("Mono Encoding Required");
        }

        // Create a buffer of 1 frame
        int[] buffer = new int[1 * numChannels];

        int cutoffHigh = 10000;
        int cutoffLow = 10000;

        int frameCounter = 0;

        boolean initializing = true;
        int previousValue = 0;
        int startOfSyncPulse = 0;
        int previousSyncLength = 238; // a reasonable guess.

        while (0 != wavIn.readFrames(buffer, 1))
        {
            if (initializing){
                // if we're at the beginning of the file, don't count
                // any initial sync pulse until we've seen a low value first.
                if (buffer[0] > cutoffHigh || frameCounter == 0) {
                    previousValue = buffer[0];
                    frameCounter++;
                    continue;
                }
                initializing = false;
            }

            if (buffer[0] > cutoffHigh && previousValue <= cutoffHigh && startOfSyncPulse == 0)
            {
                // sync going up!
                startOfSyncPulse = frameCounter;
            }
            if (buffer[0] <= cutoffLow && previousValue > cutoffLow)
            {
                // sync going down!
                if (0 == startOfSyncPulse)
                {
                    throw new Exception("Start of Sync Pulse not set!");
                }

                int syncPulseLen = frameCounter - startOfSyncPulse;
                int syncPulseCenter = (frameCounter + startOfSyncPulse)/2;

                if (syncPulseLen > 2000) {
                    // This is a frame sync
                    fileOut.write("1," + syncPulseCenter + "," + syncPulseLen + "\n");
                }
                else if (syncPulseLen < 500) {
                    // This is a line sync
                    fileOut.write("2," + syncPulseCenter + "," + syncPulseLen + "\n");
                }
                else {
                    // This is an unknown type of sync
                    fileOut.write("3," + syncPulseCenter + "," + syncPulseLen + "\n");
                }

                startOfSyncPulse = 0;
            }


            previousValue = buffer[0];
            frameCounter++;
        }


        // Close the wavFile
        wavIn.close();
        fileOut.close();

    }


    public void findFrames(String filenameIn, String filenameOut) throws Exception {
        BufferedReader fileIn = new BufferedReader(new FileReader(filenameIn));
        FileWriter fileOut = new FileWriter(filenameOut);

        int lineSyncsPerFrame = 91; // a PXL 2000 frame has 91 sync pulses
        int maxlineSyncs = 200;
        int[] lineSyncOffsets = new int[maxlineSyncs];
        int[] lineSyncDurations = new int[maxlineSyncs];

        String line = null;
        boolean lookingForFrameSync = true;
        int nextEntry = 0;
        while (null != (line = fileIn.readLine())) {
            String[] lineSplit = line.split(",");
            int entryType = Integer.parseInt(lineSplit[0]);
            int offset = Integer.parseInt(lineSplit[1]);
            int width = Integer.parseInt((lineSplit[2]));

            if (true == lookingForFrameSync && 1 != entryType) {
                // keep looking
                continue;
            } else if (true == lookingForFrameSync && 1 == entryType) {
                nextEntry = 0;
                lookingForFrameSync = false;
                continue;
            }

            if (2 != entryType){
                // complete the line
                if (nextEntry > maxlineSyncs){
                    nextEntry = maxlineSyncs;
                }

                for (int i=0; i < nextEntry; i++){
                    fileOut.write(lineSyncOffsets[i] + ", " + lineSyncDurations[i]);
                    if (i+1 < nextEntry)
                    {
                        fileOut.write(", " );
                    }
                }
                fileOut.write("\n" );
                nextEntry = 0;
                if (1 == entryType) {
                    lookingForFrameSync = false;
                }
                continue;
            }
            if (nextEntry < maxlineSyncs){
                lineSyncOffsets[nextEntry] = offset;
                lineSyncDurations[nextEntry] = width;
            }

            nextEntry++;


        }

        fileOut.close();
        fileIn.close();
    }

    // public void

}
