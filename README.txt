Compile:
  javac -cp ".:YOUR/PATH/TO/OPENCV/AR" CreateSynopsis.java
  javac -Djava.library.path=YOUR/PATH/TO/OPENCVDYLIB -cp ".:YOUR/PATH/TO/OPENCVJAR" ExploreSynopsis.java

Run:
  java CreateSynopsis YourMediaFolder synopsis.rgb
  java -Xmx6144m ExploreSynopsis synopsis.rgb

Notice:
  When compile and run CreateSynopsis, please link opencv path.

  When run ExploreSynopsis, Please set JVM max heap space as large as possible ( > 4G), because it buffered all video frames and images at once.