import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.Arrays;
import java.util.Map;

public class pxlConverter {

    private String filename;
    private static final int imageBuffLen = 128;

    pxlConverter(String file)
    {
        filename = file;

    }


    public void fftPass(Map<String, String> config, String fileIn, int videoChannel) throws Exception
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


            double val =  (int)(frame[1 + videoChannel*2] * 0x100 + (frame[0 + videoChannel*2] + 256)%256);
            imageBuff[cbuffIndex] = val;

            circularBuffIndex++;
            iterations++;

        }


        fftWav.close();
        // close the wave file
        // close the csv file
    }

    public void rollingAverage(
            Map<String, String> config,
            String filenameIn,
            String filenameOut) throws Exception
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

    public void findSyncs
            (Map<String, String> config,
             String filenameIn,
             String filenameOut) throws Exception
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


    public void findFrames(
            Map<String, String> config,
            String filenameIn,
            String filenameOut) throws Exception {
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
                    fileOut.write(lineSyncOffsets[i] + "," + lineSyncDurations[i]);
                    if (i+1 < nextEntry)
                    {
                        fileOut.write("," );
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



    public int[] getPixels1(WavFile wavIn, int previousOffset, int nextOffset, int videoChannel) throws java.io.IOException, WavFileException
    {
        // let's try something simple first.
        // For every six pixels (or rather, pixel transitions), find the steepest slope, and
        // consider that to be the "color" of the pixel.

        int numPixels = (nextOffset - previousOffset + 1) / 6;

        numPixels--;  // TODO: hack to avoid a one-off error

        int[] pixels = new int[numPixels];

        int numChannels = wavIn.getNumChannels();

        int[][] audioData = new int[numChannels][nextOffset - previousOffset + 1];

        wavIn.seek(previousOffset);

        wavIn.readFrames(audioData,0,nextOffset - previousOffset + 1);
        // This is for debug purposes
        WavFile wavOut = WavFile.newWavFile(new File("DebugWav-" + previousOffset + "-" + nextOffset +".wav"),1,3 ,16,192000);

        wavOut.writeFrames(audioData[videoChannel], nextOffset - previousOffset + 1);
        wavOut.close();



        for (int i=0; i < numPixels; i++){
            int maxDelta = 0;
            for (int j=0; j < 6; j++)
            {
                int delta = Math.abs(audioData[videoChannel][(i*6)+j+1] - audioData[videoChannel][(6*i)+j]);
                if (delta > maxDelta)
                {
                    maxDelta = delta;
                }
            }
            pixels[i] = maxDelta;
        }

        return pixels;
    }

    public int[] getPixels2(WavFile wavIn, int previousOffset, int nextOffset, int videoChannel) throws java.io.IOException, WavFileException
    {
        // let's try something simple first.
        // For every six pixels (or rather, pixel transitions), find the steepest slope, and
        // consider that to be the "color" of the pixel.


//        int numPixels = (nextOffset - previousOffset + 1) / 6;
        int samplesThisRow = (nextOffset - previousOffset + 1);

        int pixelsPerRow = 120;

        int[] pixels = new int[pixelsPerRow];

        int numChannels = wavIn.getNumChannels();

        int[][] audioData = new int[numChannels][nextOffset - previousOffset + 1];

        int lineShift = -90; // TODO: This is a tunable parameter to shift the lines to the right or left

        wavIn.seek(previousOffset + lineShift);

        wavIn.readFrames(audioData,0,nextOffset - previousOffset + 1);
        //// This is for debug purposes
        //WavFile wavOut = WavFile.newWavFile(new File("DebugWav-" + previousOffset + "-" + nextOffset +".wav"),1,3 ,16,192000);

        //wavOut.writeFrames(audioData[videoChannel], nextOffset - previousOffset + 1);
        //wavOut.close();



        for (int i=0; i < pixelsPerRow; i++){
            int maxDelta = 0;
            int pixelStart = (samplesThisRow/(pixelsPerRow+1)) * i;
            int pixelEnd = (samplesThisRow/(pixelsPerRow+1)) * (i+1);
            for (int j=pixelStart; j < pixelEnd; j++)
            {
                int delta = Math.abs(audioData[videoChannel][j] - audioData[videoChannel][j+1]);
                if (delta > maxDelta)
                {
                    maxDelta = delta;
                }
            }
            pixels[i] = maxDelta;
        }

        return pixels;
    }

    public int[] getPixels3(WavFile wavIn, int previousOffset, int nextOffset, int videoChannel) throws java.io.IOException, WavFileException
    {
        // let's try something simple first.
        // For every six pixels (or rather, pixel transitions), find the steepest slope, and
        // consider that to be the "color" of the pixel.


//        int numPixels = (nextOffset - previousOffset + 1) / 6;
        int samplesThisRow = (nextOffset - previousOffset + 1);

        int pixelsPerRow = 120;

        int[] pixels = new int[pixelsPerRow];

        int numChannels = wavIn.getNumChannels();

        int[][] audioData = new int[numChannels][nextOffset - previousOffset + 1];

        int lineShift = -90; // TODO: This is a tunable parameter to shift the lines to the right or left

        wavIn.seek(previousOffset + lineShift);

        wavIn.readFrames(audioData,0,nextOffset - previousOffset + 1);
        //// This is for debug purposes
        //WavFile wavOut = WavFile.newWavFile(new File("DebugWav-" + previousOffset + "-" + nextOffset +".wav"),1,3 ,16,192000);

        //wavOut.writeFrames(audioData[videoChannel], nextOffset - previousOffset + 1);
        //wavOut.close();



        for (int i=0; i < pixelsPerRow; i++){
            int maxDelta = 0;
            int pixelStart = (samplesThisRow/(pixelsPerRow+1)) * i;
            int pixelEnd = (samplesThisRow/(pixelsPerRow+1)) * (i+1);
            int max = audioData[videoChannel][pixelStart];
            int min = audioData[videoChannel][pixelStart];
            for (int j=pixelStart; j <= pixelEnd; j++)
            {
                if (min > audioData[videoChannel][j])
                {
                    min = audioData[videoChannel][j];
                }
                if (max < audioData[videoChannel][j]){
                    max = audioData[videoChannel][j];
                }

            }
            pixels[i] = max-min;
        }

        return pixels;
    }

    public int[] getPixels4(WavFile wavIn, int previousOffset, int nextOffset, int videoChannel) throws java.io.IOException, WavFileException
    {
        // let's try something simple first.
        // For every six pixels (or rather, pixel transitions), find the steepest slope, and
        // consider that to be the "color" of the pixel.


//        int numPixels = (nextOffset - previousOffset + 1) / 6;
        int samplesThisRow = (nextOffset - previousOffset + 1);

        int pixelsPerRow = 360;
        int pixelsRowWidth = 150;

        int[] pixels = new int[pixelsPerRow];

        int numChannels = wavIn.getNumChannels();

        int[][] audioData = new int[numChannels][nextOffset - previousOffset + 1];

        int lineShift = -50; // TODO: This is a tunable parameter to shift the lines to the right or left

        wavIn.seek(previousOffset + lineShift);

        wavIn.readFrames(audioData,0,nextOffset - previousOffset + 1);
        //// This is for debug purposes
        //WavFile wavOut = WavFile.newWavFile(new File("DebugWav-" + previousOffset + "-" + nextOffset +".wav"),1,3 ,16,192000);

        //wavOut.writeFrames(audioData[videoChannel], nextOffset - previousOffset + 1);
        //wavOut.close();

        int paddingNeeded = samplesThisRow/(pixelsRowWidth+1) - (samplesThisRow/(pixelsPerRow+1));
        int unpaddedSamples = samplesThisRow - paddingNeeded;

        for (int i=0; i < pixelsPerRow; i++){
            int maxDelta = 0;
            int pixelStart = (unpaddedSamples/(pixelsPerRow+1)) * i;
            int pixelEnd = (unpaddedSamples/(pixelsPerRow+1)) * i + unpaddedSamples/(pixelsRowWidth+1);
            for (int j=pixelStart; j < pixelEnd; j++)
            {
                int delta = Math.abs(audioData[videoChannel][j] - audioData[videoChannel][j+1]);
                if (delta > maxDelta)
                {
                    maxDelta = delta;
                }
            }
            pixels[i] = maxDelta;
        }

        return pixels;
    }


    public void saveImage(int[][] pixels, String imageFilename) throws java.io.IOException
    {
        int numRows = 0;
        int maxCols = 0;

        try{
            for (int i=0; i < pixels.length; i++){
                if (pixels[i].length > maxCols){
                    maxCols = pixels[i].length;

                }
                numRows++;
            }
        }
        catch (java.lang.NullPointerException e)
        {
            int i = 3;
            // we were expecting it. planning on it really.
            // It was basically a "break" statement.  Ignore and continue.
        }

        /*

        int[][] pixelData = new int[maxCols][numRows];

        for (int currentRow = 0; currentRow < numRows; currentRow++)
        {
            for (int currentColumn = 0; currentColumn < pixels[currentRow].length; currentColumn++){
                pixelData[currentColumn][currentRow] = pixels[currentRow][currentColumn];
            }
        }
        */


        int maxVal = 0;
        int[] allVals = new int[maxCols * numRows];
        int numPixels = 0;
        for (int x=0; x < maxCols; x++) {
            for (int y = 0; y < numRows; y++) {
                if (x < pixels[y].length) {
                    if (pixels[y][x] > maxVal) {
                        maxVal = pixels[y][x];
                    }
                    allVals[numPixels] = pixels[y][x];
                    numPixels++;
                }
            }
        }

        Arrays.sort(allVals);
        maxVal = allVals[numPixels * 85 / 100];

        int[] imageBuffer = new int[(3*numRows) * maxCols];

        for (int x=0; x < maxCols; x++){
            for (int y = 0; y < numRows; y++) {
                if (x < pixels[y].length) {
                    //                    int pixelValue = pixels[y][x] / 2;  //TODO: The value 10 here is a tunable parameter for brightness adjustment.
                    int pixelValue = pixels[y][x] * 255 / maxVal;  //TODO: The value 10 here is a tunable parameter for brightness adjustment.
                    if (pixelValue > 255) {
                        pixelValue = 255;
                    }
                    imageBuffer[((3*y) * maxCols) + x] = 255 - pixelValue;
                    imageBuffer[((3*y + 1) * maxCols) + x] = 255 - pixelValue;
                    imageBuffer[((3*y + 2) * maxCols) + x] = 255 - pixelValue;
                } else {
                    imageBuffer[((3*y) * maxCols) + x] = 0;
                    imageBuffer[((3*y + 1) * maxCols) + x] = 0;
                    imageBuffer[((3*y + 2) * maxCols) + x] = 0;
                }

            }

        }

        BufferedImage image = new BufferedImage(maxCols, numRows*3, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = image.getRaster();
        raster.setPixels(0, 0, maxCols, numRows*3, imageBuffer);

        ImageIO.write(image, "png", new File(imageFilename));

    }


    public void drawFrames(
            Map<String, String> config,
            String framesFilenameIn,
            String originalFilenameIn,
            String ImagePrefix,
            int videoChannel) throws Exception {
        BufferedReader fileIn = new BufferedReader(new FileReader(framesFilenameIn));
//        WavFile wavIn = WavFile.openWavFile(new File(originalFilenameIn));


        int currentImage = 0;
        int maxRows = 100;
        int[][] pixels = new int[maxRows][];
        String line;
        while (null != (line = fileIn.readLine())) {
            String[] lineSplit = line.split(",");

            int lineIndex = 0;
            int previousOffset = Integer.parseInt(lineSplit[0]);
            for (int i=2; i < lineSplit.length; i+= 2) {
                int lineSyncOffset = Integer.parseInt(lineSplit[i]);
                // line starts on previousOffset and ends on lineSyncOffset

                WavFile wavIn = WavFile.openWavFile(new File(originalFilenameIn));

                //pixels[lineIndex] = getPixels1(wavIn, previousOffset, lineSyncOffset, videoChannel);
                pixels[lineIndex] = getPixels4(wavIn, previousOffset, lineSyncOffset, videoChannel);
                //pixels[lineIndex] = getPixels3(wavIn, previousOffset, lineSyncOffset, videoChannel);

                wavIn.close();

                previousOffset = lineSyncOffset;
                lineIndex++;
            }

            saveImage(pixels, ImagePrefix + currentImage + ".png");

            currentImage++;
        }

        //wavIn.close();

    }

    // creates a special WAV file where all locations are visibly unique for debug reasons
    public void createNonRepeatingWav() throws Exception
    {
        WavFile wavOut = WavFile.newWavFile(new File("index.wav"),2,3 ,16,192000);

        int multiplier = 10;
        int[] sampleOut = new int[4];
        for (int i=0; i < 32000 * multiplier; i++)
        {
            sampleOut[0] = -(i/multiplier) ;
            sampleOut[2] = (i* 100) % 32000;
            sampleOut[1] = (i % 64000) - 32000;
            sampleOut[3] = (i % 64000) - 32000;

            wavOut.writeFrames(sampleOut,0, 2);

        }

        wavOut.close();

    }

    public void copyNonRepeatingWav() throws Exception
    {
        WavFile wavOut = WavFile.newWavFile(new File("indexCopy.wav"),2,3 ,16,192000);
        WavFile wavIn = WavFile.openWavFile(new File("index.wav"));


        int multiplier = 10;
        int[] sampleOut = new int[2];
        int[][] audioData = new int[2][1];
        for (int i=0; i < 32000 * multiplier * 2; i++)
        {

            if (i >0 && (i % 47234)== 0) {
                wavIn.seek(i);
            }

            wavIn.readFrames(audioData,0,1);

            wavOut.writeFrames(audioData,1);

//            wavOut.writeFrames(sampleOut,0, 2);

        }

        wavOut.close();

    }


        // public void

}
