import java.io.*;
import javax.imageio.*;
import java.awt.*;
import java.awt.image.*;


class MetaData implements Serializable {
  String filePath;  // video frame folder path
  boolean isVideo;
  String audioPath;
  int hUpper;
  int hLower;
  int wUpper;
  int wLower;
}


class BufferedImageWithMetaData implements Serializable {
  MetaData[] metaData;
  BufferedImage img;

  /*
   * Constructor
   */
  public BufferedImageWithMetaData(int synopsisFrameNum, int wSynopsis, int hSynopsis)
  {
    this.metaData = new MetaData[synopsisFrameNum];
    for (int i = 0; i < synopsisFrameNum; i++) {
      this.metaData[i] = new MetaData();
    }
    this.img = new BufferedImage(wSynopsis, hSynopsis, BufferedImage.TYPE_INT_RGB);
  }

  public void writeObject(ObjectOutputStream out) {
    try {
      out.writeObject(metaData);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      ImageIO.write(img, "png", buffer);
      out.writeInt(buffer.size());
      buffer.writeTo(out);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void readObject(ObjectInputStream in) {
    try {
      metaData = (MetaData[]) in.readObject();
      int size = in.readInt();
      byte[] buffer = new byte[size];
      in.readFully(buffer);
      img = ImageIO.read(new ByteArrayInputStream(buffer));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
