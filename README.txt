Compile:
  javac CreateSynopsis.java
  javac ExploreSynopsis.java

Run:
  java CreateSynopsis YourMediaFolder synopsis.rgb
  java -Xmx6144m synopsis.rgb

Notice:
  Please set JVM max heap space as large as possible ( > 4G), because it buffered all video frames and images at once.