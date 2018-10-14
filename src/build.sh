#!/bin/bash

javac *.java
jar -cvfm PxlMagic.jar MANIFEST.MF *.class
java  -jar PxlMagic.jar $@


