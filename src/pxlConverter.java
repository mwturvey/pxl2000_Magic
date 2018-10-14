import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.*;

public class pxlConverter {

    private String filename;
    private static final int imageBuffLen = 128;

    pxlConverter(String file)
    {
        filename = file;

    }


    public void fftPass(Map<String, String> config, String fileIn, String fileOut, int videoChannel) throws Exception
    {
        File dir = new File(config.get("directory"));
//        if (null == fileIn){
//            fileIn = filename;
//        }

        // In the first pass, we'll do the FFT of the input file.
//        String firstPassFilename = fileIn.replaceAll("\\.wav",".fft.wav");
        File outFile = new File(dir, fileOut);
        if (outFile.exists()){
            System.out.println("Skipping FFT Pass.  (" + fileOut + " already exists.)");
            return;
        }
        System.out.println("Performing FFT Pass.");

        WavFile fftWav = WavFile.newWavFile(outFile,1,150000 * 60 * 60 * 2 ,16,192000);

        boolean outputCsv = false;
        File csvOutFile = new File(dir, "fft.csv");
        FileWriter csvOut = null;
        if (config.get("fft_csv") == "true") {
            csvOut = new FileWriter(config.get("directory") + "/" + "fft.csv");
            outputCsv = true;
        }



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
                //int imageBuffIndex = (circularBuffIndex-1 + imageBuffLen) % imageBuffLen;
                //sampleOut[0] = (int)imageBuff[(circularBuffIndex-1 + imageBuffLen) % imageBuffLen];
                //double foo = realBuffer[imageBuffLen-1];
                //double foo2 = realBuffer[imageBuffLen-2];
                //int indexToSample = 9;
//                sampleOut[0] = (int)Math.sqrt(realBuffer[5] * realBuffer[5] + imaginaryBuffer[5] * imaginaryBuffer[5]);
//                sampleOut[0] = (int)Math.sqrt(realBuffer[indexToSample] * realBuffer[indexToSample] + imaginaryBuffer[indexToSample] * imaginaryBuffer[indexToSample]);
                sampleOut[0] = (int)Math.sqrt(realBuffer[9] * realBuffer[9] + imaginaryBuffer[9] * imaginaryBuffer[9]) +
                               (int)Math.sqrt(realBuffer[10] * realBuffer[10] + imaginaryBuffer[10] * imaginaryBuffer[10]) ;
//                sampleOut[0] = (int)Math.max(realBuffer[12]*imaginaryBuffer[12], Math.max(realBuffer[13]*imaginaryBuffer[13], Math.max(realBuffer[14]*imaginaryBuffer[14],realBuffer[15]*imaginaryBuffer[15])));
//                sampleOut[0] = (int)Math.sqrt(sampleOut[0]);



//                List<Integer> fftTaps = new ArrayList<Integer>();
//                fftTaps.add(9);
//                fftTaps.add(10);
//                fftTaps.add(11);
//                fftTaps.add(12);
//                fftTaps.add(13);
//
//                double sample = 0;
//                for (Integer tap: fftTaps)
//                {
//                    double tmp = realBuffer[tap]*imaginaryBuffer[tap];
//                    if (tmp > sample){
//                        sample = tmp;
//                    }
//                }
//                sampleOut[0] = (int)Math.sqrt(sample);



//                sampleOut[0] = (int)Math.max(realBuffer[9]*imaginaryBuffer[9], Math.max(realBuffer[10]*imaginaryBuffer[10], Math.max(realBuffer[11]*imaginaryBuffer[11],realBuffer[12]*imaginaryBuffer[12])));
//                sampleOut[0] = (int)Math.sqrt(sampleOut[0]);
//                // normalize
//                sampleOut[0] = sampleOut[0] / 1;
                sampleOut[0] = sampleOut[0] / 16;
                if (sampleOut[0] > 30000) {
                    sampleOut[0] = 30000;
                }
                fftWav.writeFrames(sampleOut,0, 1);
                // output the results to a wav file
                // output the results to a csv file
                if (outputCsv && iterations < 100000) {
                    int output = 0;
                    int samplesToOutput = 20;
                    for (int i=0; i < samplesToOutput; i++) {
                        output = (int)Math.sqrt(realBuffer[i] * realBuffer[i] + imaginaryBuffer[i] * imaginaryBuffer[i]);
                        csvOut.write(output + ",");
                    }
                    output = (int)Math.sqrt(realBuffer[samplesToOutput] * realBuffer[samplesToOutput] + imaginaryBuffer[samplesToOutput] * imaginaryBuffer[samplesToOutput]);
                    csvOut.write(output + "\n");
                }

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


        // close the wave file
        fftWav.close();
        // close the csv file

        if (outputCsv)
        {
            csvOut.close();
        }
    }

    public void rollingAverage(
            Map<String, String> config,
            String filenameIn,
            String filenameOut) throws Exception
    {
        File dir = new File(config.get("directory"));

        File outFile = new File(dir, filenameOut);
        if (outFile.exists()){
            System.out.println("Skipping FFT Averaging Pass.  (" +filenameOut + " already exists.)");
            return;
        }

        System.out.println("Performing FFT Averaging Pass.");

        WavFile wavIn = WavFile.openWavFile(new File(dir, filenameIn));
        WavFile wavOut = WavFile.newWavFile(outFile,1,3 ,16,192000);


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
        File dir = new File(config.get("directory"));

        File outFile = new File(dir, filenameOut);
        if (outFile.exists()){
            System.out.println("Skipping Sync Finding Pass.  (" +filenameOut + " already exists.)");
            return;
        }

        System.out.println("Performing Sync Finding Pass.");

        WavFile wavIn = WavFile.openWavFile(new File(dir, filenameIn));
        //WavFile wavOut = WavFile.newWavFile(new File(filenameOut),1,3 ,16,192000);
        FileWriter fileOut = new FileWriter(config.get("directory") + "/" +filenameOut);
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
        File dir = new File(config.get("directory"));

        File outFile = new File(dir, filenameOut);
        if (outFile.exists()){
            System.out.println("Skipping Frame Finding Pass.  (" +filenameOut + " already exists.)");
            return;
        }

        System.out.println("Performing Frame Finding Pass.");

        BufferedReader fileIn = new BufferedReader(new FileReader(config.get("directory") + "/" + filenameIn));
        FileWriter fileOut = new FileWriter(config.get("directory") + "/" + filenameOut);

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

                // TODO: use a stringbuilder
                int counter = 0;
                String tmpLine = "";

                for (int i=0; i < nextEntry; i++){
                    tmpLine = tmpLine + lineSyncOffsets[i] + "," + lineSyncDurations[i];
                    counter++;
//                    fileOut.write(lineSyncOffsets[i] + "," + lineSyncDurations[i]);
                    if (i+1 < nextEntry)
                    {
                        tmpLine = tmpLine + ",";
//                        fileOut.write("," );
                    }
                }

                if (counter > 50) {
                    // TODO: figure out why this is happening.  Look at Test19 which comes form Birthday ca sample 333000000
                    fileOut.write(tmpLine + "\n");
                }
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

    public int[] getPixels4(
            WavFile wavIn,
            int previousOffset,
            int nextOffset,
            int videoChannel
            ) throws java.io.IOException, WavFileException
    {
        // let's try something simple first.
        // For every six pixels (or rather, pixel transitions), find the steepest slope, and
        // consider that to be the "color" of the pixel.

        //previousOffset -= 100;
        //nextOffset += 100;

//        int numPixels = (nextOffset - previousOffset + 1) / 6;
        int samplesThisRow = (nextOffset - previousOffset + 1);

        int pixelsPerRow = 360;
//        int pixelsRowWidth = 80;

        int[] pixels = new int[pixelsPerRow];

        int numChannels = wavIn.getNumChannels();

        assert (previousOffset < nextOffset);

        int[][] audioData = new int[numChannels][nextOffset - previousOffset + 1];

        int lineShift = -104; // TODO: This is a tunable parameter to shift the lines to the right or left

        wavIn.seek(previousOffset + lineShift);

        wavIn.readFrames(audioData,0,samplesThisRow);
        //// This is for debug purposes
        //WavFile wavOut = WavFile.newWavFile(new File("DebugWav-" + previousOffset + "-" + nextOffset +".wav"),1,3 ,16,192000);

        //wavOut.writeFrames(audioData[videoChannel], nextOffset - previousOffset + 1);
        //wavOut.close();

        int overlap = 15;
        int samplesWithoutOverlap = samplesThisRow - overlap;

        //int paddingNeeded = samplesThisRow/(pixelsRowWidth+1) - (samplesThisRow/(pixelsPerRow+1));
        //int unpaddedSamples = samplesThisRow - paddingNeeded;

        for (int i=0; i < pixelsPerRow; i++){
            int maxDelta = 0;
//            int pixelStart = (unpaddedSamples/(pixelsPerRow+1)) * i;
//            int pixelEnd = pixelStart + unpaddedSamples/(pixelsRowWidth+1);
            int pixelStart = (int)((((double)samplesWithoutOverlap)/((double)(pixelsPerRow))) * (double)i);
            int pixelEnd = pixelStart + overlap;
            int max = audioData[videoChannel][pixelStart];
            int min = audioData[videoChannel][pixelStart];

            for (int j=pixelStart; j < pixelEnd; j++)
            {
                int delta = Math.abs(audioData[videoChannel][j] - audioData[videoChannel][j+1]);
                if (delta > maxDelta)
                {
                    maxDelta = delta;
                }

                if (audioData[videoChannel][j] < min) {min = audioData[videoChannel][j];}
                if (audioData[videoChannel][j] > max) {max = audioData[videoChannel][j];}
            }
            pixels[i] = maxDelta; // looking for the largest slope to determine pixel value
//            pixels[i] = max-min; // looking for largest absolute (peak-to-peak) value to determine pixel value
        }

        return pixels;
    }


    public void saveImage(int[][] pixels, File dir, String imageFilename) throws java.io.IOException
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
        maxVal = (int)(allVals[numPixels * 85 / 100] * 1.2);

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

        ImageIO.write(image, "png", new File(dir, imageFilename));

    }


    private int[] annealFrame(String[] frame, int iterations, double strength)
    {
        int frameLength = frame.length;

        double[] result = new double[frameLength/2];
        double[] oldResult = new double[frameLength/2];

        for (int i=0; i < frameLength; i+=2) {
            result[i/2] = Integer.parseInt(frame[i]);
        }

        for (int iteration=0; iteration < iterations; iteration++){

            for (int i=0; i < result.length; i++) {
                oldResult[i] = result[i];
            }

            for (int i=1; i < (result.length - 1); i++) {
                result[i] = (oldResult[i] + (oldResult[i-1]  * strength) + (oldResult[i+1] * strength)) /
                                (1+ (2 * strength));
            }
        }

        int[] intResult = new int[result.length];

        for (int i=0; i < result.length; i++) {
            intResult[i] = (int)(result[i]);
        }

        return intResult;
    }

    public void drawFrames(
            Map<String, String> config,
            String framesFilenameIn,
            String originalFilenameIn,
            String ImagePrefix,
            int videoChannel) throws Exception {
        File dir = new File(config.get("directory"));
        BufferedReader fileIn = new BufferedReader(new FileReader(config.get("directory") + "/" +framesFilenameIn));
//        WavFile wavIn = WavFile.openWavFile(new File(originalFilenameIn));
        System.out.println("Drawing Frames.");


        int currentImage = 0;
        int maxRows = 100;
        int[][] pixels = new int[maxRows][];
        String line;
        while (null != (line = fileIn.readLine())) {
            String[] lineSplit = line.split(",");

            int[] frame = annealFrame(lineSplit, 0, 0.5);

            int lineIndex = 0;
            int previousOffset = frame[0];
            for (int i=1; i < frame.length; i++) {
                int lineSyncOffset = frame[i];
                // line starts on previousOffset and ends on lineSyncOffset

                if (lineIndex <  90) {
                    WavFile wavIn = WavFile.openWavFile(new File(originalFilenameIn));

                    //pixels[lineIndex] = getPixels1(wavIn, previousOffset, lineSyncOffset, videoChannel);
                    pixels[lineIndex] = getPixels4(wavIn, previousOffset, lineSyncOffset, videoChannel);
                    //pixels[lineIndex] = getPixels3(wavIn, previousOffset, lineSyncOffset, videoChannel);

                    wavIn.close();

                }
                else {
                    int a=0;
                }
                previousOffset = lineSyncOffset;
                lineIndex++;
            }

            saveImage(pixels, dir, ImagePrefix + String.format("%05d", currentImage) + ".png");

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

    public void findValidSoundLocations(
            Map<String, String> config,
            String framesFilenameIn,
            String filenameOut)  throws Exception
    {
        BufferedReader fileIn = new BufferedReader(new FileReader(config.get("directory") + "/" + framesFilenameIn));
        FileWriter fileOut = new FileWriter(config.get("directory") + "/" +filenameOut);

        System.out.println("Figuring out where to use the sound.");

        int previousFrame = 0;
        List<Integer> frameLengths = new ArrayList<Integer>();
        List<Integer> frameStarts = new ArrayList<Integer>();
        String line;
        while (null != (line = fileIn.readLine())) {
            String[] lineSplit = line.split(",");
            int frameStart = Integer.parseInt((lineSplit[0]));

            frameStarts.add(frameStart);

            if (previousFrame != 0){
                frameLengths.add(frameStart-previousFrame);
            }

            previousFrame = frameStart;
        }

        frameLengths.sort((x, y) -> y-x);
        int baseFrameLength = frameLengths.get(frameLengths.size() / 2);

        // for audio clipping purposes,
        // allow frames up to 2.5 times the median frame length.
        // if there are a lot of "frame skips" then this
        // might cause issues with audio getting notably out of sync.
        baseFrameLength = baseFrameLength * 250 / 100;

        // this will store the start and stopping points of all of the audio we wish to keep
        List<Map.Entry<Integer,Integer>> audioSegments = new ArrayList<Map.Entry<Integer,Integer>>();

        previousFrame = 0;
        int startFrame = 0;

        for (int i = 0; i < frameStarts.size() -1; i++) {
            int thisFramePos = frameStarts.get(i);
            int thisFrameLength = thisFramePos - previousFrame;
            if (0 != startFrame
                    && thisFrameLength > baseFrameLength) {
                // we've jumped to far, wrap up the previous run as an audio segment and carry on
                audioSegments.add(new AbstractMap.SimpleEntry<Integer, Integer>(startFrame, previousFrame));

                startFrame = 0;
            }

            if (0 == startFrame) {
                startFrame = thisFramePos;
            }

            previousFrame = thisFramePos;
        }

        // and add one last segment for the last one we were working on
        audioSegments.add(new AbstractMap.SimpleEntry<Integer, Integer>(startFrame, previousFrame));



//        WavFile wavOut = WavFile.newWavFile(new File("sampleOut.wav"),1,3 ,16,44100);


        for (Map.Entry<Integer, Integer> segment: audioSegments) {
            fileOut.write(segment.getKey() + "," + segment.getValue() + "\n");

        }
        fileOut.close();

    }

    public void generateFinalAudio(
            Map<String, String> config,
            String sourceWavFilename,
            String audioLocationsFilename,
            String filenameOut,
            int videoChannel)  throws Exception
    {

        System.out.println("Generating Final Audio.");

        File dir = new File(config.get("directory"));

        File outFile = new File(dir, filenameOut);
//        if (outFile.exists()){
//            System.out.println("Skipping Frame Finding Pass.  (" +filenameOut + " already exists.)");
//            return;
//        }

        BufferedReader fileIn = new BufferedReader(new FileReader(config.get("directory") + "/" + audioLocationsFilename));

        WavFile wavOut = WavFile.newWavFile(outFile,1,3 ,16,44100);

        int audioChannel = 1-videoChannel;

        String line;
        while (null != (line = fileIn.readLine())) {
            String[] lineSplit = line.split(",");

            int frameStart = Integer.parseInt((lineSplit[0]));
            int frameEnd = Integer.parseInt((lineSplit[1]));

            WavFile wavIn = WavFile.openWavFile(new File(sourceWavFilename));

            wavIn.seek(frameStart);
            int[][] singleSample = new int[2][1];

            for (int i=frameStart; i < frameEnd; i++) {
                wavIn.readFrames(singleSample, 1);

                if (((i-frameStart) % 35) == 0) {
                    wavOut.writeFrames(singleSample[audioChannel], 1);
                }
            }
            wavIn.close();
        }

        wavOut.close();


    }


    // public void

}
