# About
<center><img src="https://lh3.googleusercontent.com/gkDQXp9fKeEi0k2wiZ1KgMbIVv1kkA5VXrliS_CzkdsFuuAZ4ujG9SWddNy9IN-PO1Zofh5nR2t-2kuN6s-nTA5V7lZfUpeXqfXD9WmZkc3Yh6TJBMQwxrvJJ6W1BHZx64A16mlj6ro=w2400" alt="drawing" width="400"/></center>

The PXL2000 is a video camera manufactured by Fisher Price in the late 1980's.  It's unique in that it records on cassette tapes instead of video tape.  

This project's aim is to convert a WAV file of such a cassette recording into the corresponding video with as high of a quality as possible.  

# Related
I've considered writing this tool for a while, and as I started getting more serious, I came across the work of Kevin Seifert.  He's already done a ton of work decoding the format and written an decoder here: https://github.com/sevkeifert/pxl-2000-decoder

My experience with that decoder is that, while it does an awesome job in that you can make out what is on the video (truly a big accomplishment), it struggles to reliably find line and even frame syncs.  I started this project with the intent of improving that project, but quickly realized that my overall approach was different enough that it would be easier to start coding from scratch.

# Approach
With the overall goal of "the best video quality possible, given the original signal," I chose to limit this project to using WAV file as the input (no streaming support).  It is built as a series of filters that run one after the other.  It identifies the sync signals by running a Fourier Transform on the input stream, looking for the 15 Khz sync signals.  This approach appears to work very well for identifying the signals, but at the cost of taking a long time to compute.  

<img src = "https://lh3.googleusercontent.com/vpamQgvN8O4BGH_ZMDXHTlYOKkiC5LmV04jL0JX9soZ0GsEOrAUsB9fu5cxOUzeOjNgYVGwJiMfYvJJmOipRUszqnql0xD53m6YFmqYTz3IBLydKpZhIaJhJFER5lLIjsCE2O4sA4AM=w2400" alt="drawing" width="700"/>

In this image, you can see the original PXL 2000 signal in the top two rows.  First is the video signal, followed by the audio channel.  The third row is the output of an FFT transform looking for the sync signal, and the fourth row is simply a smoothed version of the third row.

I'm decomposing the problem by first trying to reliably detect the sync signals-- especially the frame syncs.  Once the frame sync signals are captured, I can treat each frame as an object.  I know that each frame will have the same number of scan lines, each terminating in a short sync pulse.  And I know that each of those per-line sync pulses will be evenly spaced (since each line  in the video image is obviously the same length).  This allows me to reliably decompose the frame into scanlines.  And then, I can treat each scanline as an object itself.  
