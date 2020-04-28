import java.util.ArrayList;
import java.util.Arrays;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;



public class CreateSynopsis {
  static int iw = 352;
  static int ih = 288;
  String folderPath;
  String synopsisPath;
  BufferedImageWithMetaData outImg;
  static int synopsisFrameNum = 20;
  static int hFrameNum = 2;
  static int wFrameNum = synopsisFrameNum / hFrameNum;
  static int ow = (iw/2) * wFrameNum;
  static int oh = (ih/2) * hFrameNum;
  ArrayList<String[]> frameFolderList;
  String[] imgList;
  ArrayList<String> audioList;

  public CreateSynopsis(String folderPath, String synopsisPath)
  {
    this.folderPath = folderPath;
    this.synopsisPath = synopsisPath;
    this.outImg = new BufferedImageWithMetaData(synopsisFrameNum, ow, oh);
    this.frameFolderList = new ArrayList<String[]>();
    this.audioList = new ArrayList<String>();
    getFrameNameList();
  }

  public void getFrameNameList()
  {
    File files = new File(folderPath);
    File[] fileList = files.listFiles();
    Arrays.sort(fileList);
    for (int i = 0; i < fileList.length; i++) {
      if (fileList[i].isDirectory()) {
        if (fileList[i].getName().equals("Image")) {  // handle ./Image/RGB/
          File[] subfileList = new File(fileList[i].getPath()).listFiles();
          for (int j = 0; j < subfileList.length; j++) {
            if (subfileList[j].getName().equals("RGB")) {
              File[] imgFiles = new File(subfileList[j].getPath()).listFiles();
              Arrays.sort(imgFiles);
              imgList = new String[imgFiles.length];
              for (int k = 0; k < imgFiles.length; k++) {
                imgList[k] = imgFiles[k].getPath();
              }
            }
          }
        } else {  // handle ./XXXVideoN/
          File[] frameFiles = new File(fileList[i].getPath()).listFiles();
          Arrays.sort(frameFiles);
          String[] frameList = new String[frameFiles.length];
          for (int j = 0; j < frameFiles.length; j++) {
            frameList[j] = frameFiles[j].getPath();
          }
          frameFolderList.add(frameList);
        }
      } else {
        String fileName = fileList[i].getName();
        String fileType = fileName.substring(fileName.lastIndexOf("."));
        if (fileType.equals(".wav")) {
          audioList.add(fileList[i].getPath());
        }
      }
    }
    if (audioList.size() != frameFolderList.size()) {
      System.err.println("audio number does not match video folder number");
      System.exit(1);
    }
  }

  public void chooseSynopsisNaive()
  {
    // choose key video frame
    int synopsisFrameNumEachVideoOrImg = synopsisFrameNum / (frameFolderList.size() + 1);
    for (int i = 0; i < frameFolderList.size(); i++) {
      int videoFrameNum = frameFolderList.get(i).length;
      for (int j = 0; j < synopsisFrameNumEachVideoOrImg; j++) {
        outImg.metaData[i*synopsisFrameNumEachVideoOrImg + j].filePath = 
          frameFolderList.get(i)[videoFrameNum / synopsisFrameNumEachVideoOrImg * j];
        outImg.metaData[i*synopsisFrameNumEachVideoOrImg + j].isVideo = true;
        outImg.metaData[i*synopsisFrameNumEachVideoOrImg + j].audioPath = audioList.get(i);
      }
    }
    // choose key image
    int imgNum = imgList.length;
    for (int j = 0; j < synopsisFrameNumEachVideoOrImg; j++) {
      outImg.metaData[frameFolderList.size()*synopsisFrameNumEachVideoOrImg + j].filePath = 
        imgList[imgNum / synopsisFrameNumEachVideoOrImg * j];
    }
  }

  public void encodeOutImgLayoutNaive()
  {
    float wLenPerFrame = (float)ow / wFrameNum;
    float hLenPerFrame = (float)oh / hFrameNum;
    for (int hImg = 0; hImg < hFrameNum; hImg++) {
      for (int wImg = 0; wImg < wFrameNum; wImg++) {
        // set meta information
        int hUpper = (int)((hImg+1)*hLenPerFrame);
        int hLower = (int)(hImg*hLenPerFrame);
        int wUpper = (int)((wImg+1)*wLenPerFrame);
        int wLower = (int)(wImg*wLenPerFrame);
        outImg.metaData[hImg*wFrameNum + wImg].hUpper = hUpper;
        outImg.metaData[hImg*wFrameNum + wImg].hLower = hLower;
        outImg.metaData[hImg*wFrameNum + wImg].wUpper = wUpper;
        outImg.metaData[hImg*wFrameNum + wImg].wLower = wLower;
        // fill in image pixel
        BufferedImage inImg = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        readImageRGB(iw, ih, outImg.metaData[hImg*wFrameNum + wImg].filePath, inImg);
        for (int h = hLower; h < hUpper; h++) {
          for (int w = wLower; w < wUpper; w++) {
            outImg.img.setRGB(w, h, inImg.getRGB((int)((w%(int)wLenPerFrame)/wLenPerFrame*iw), (int)((h%(int)hLenPerFrame)/hLenPerFrame*ih)));
          }
        }
      }
    }
  }

  public void serializeOutImg()
  {
    try {
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(synopsisPath));
      outImg.writeObject(oos);
      oos.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * deserialize outImg, for debug
   */
  public void deserializeOutImg()
  {
    try {
      File file = new File(synopsisPath);
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
      BufferedImageWithMetaData a = new BufferedImageWithMetaData(synopsisFrameNum, ow, oh);
      a.readObject(ois);
      showIm(a.img);
      System.out.println(a.metaData[0].filePath);
      ois.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * Show outImg, for debug
   */
  public void showIm(BufferedImage img)
  {
    JFrame frame = new JFrame();
    GridBagLayout gLayout = new GridBagLayout();
    frame.getContentPane().setLayout(gLayout);

    JLabel lbIm1 = new JLabel(new ImageIcon(img));

    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.CENTER;
    c.weightx = 0.5;
    c.gridx = 0;
    c.gridy = 0;

    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = 1;
    frame.getContentPane().add(lbIm1, c);

    frame.pack();
    frame.setVisible(true);
  }

  public void readImageRGB(int width, int height, String imgPath, BufferedImage img)
  {
    try {
      int frameLength = width*height*3;
      File file = new File(imgPath);
      RandomAccessFile raf = new RandomAccessFile(file, "r");
      raf.seek(0);
      long len = frameLength;
      byte[] bytes = new byte[(int) len];
      raf.read(bytes);
      raf.close();

      int ind = 0;
      for(int y = 0; y < height; y++) {
        for(int x = 0; x < width; x++) {
          // byte a = 0;
          byte r = bytes[ind];
          byte g = bytes[ind+height*width];
          byte b = bytes[ind+height*width*2]; 
          int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
          //int pix = ((a << 24) + (r << 16) + (g << 8) + b);
          img.setRGB(x,y,pix);
          ind++;
        }
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args)
  {
    if (args.length != 2) {
      System.err.println("Arguments format error. It should be: folderPath, synopsisPath.");
      System.exit(1);
    }
    String folderPath = args[0];
    String synopsisPath = args[1];
    CreateSynopsis cs = new CreateSynopsis(folderPath, synopsisPath);
    cs.chooseSynopsisNaive();
    cs.encodeOutImgLayoutNaive();
    cs.serializeOutImg();
    //cs.showIm(cs.outImg.img);
    //cs.deserializeOutImg();
  }
}