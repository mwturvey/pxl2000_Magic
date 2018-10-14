#/bin/sh

outVid='out.mp4'
file=$1

if [ -f $file ]; then
	dir=$( echo $file | sed -e 's/\.wav$//')
	echo "Processing: $file.."
	java -jar PxlMagic.jar $file
	echo "Extracting $dir/$outVid.."
	(cd $dir && ffmpeg -r 15 -f image2 -i image%05d.png -i final_audio.wav $outVid)
	echo "Done!"
else
	echo "Usage: $0 [192kHz PXL2000 .wav file]"
fi
