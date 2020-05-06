import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.*;
import javax.imageio.*;


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
  static int searchRange = 50;  // from avg-sr to avg+sr, 2*sr+1

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
        if (fileList[i].getName().toLowerCase().equals("image")) {  // handle ./image/
          File[] imgFiles = new File(fileList[i].getPath()).listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
              return !file.isHidden();
            }
          });
          Arrays.sort(imgFiles);
          imgList = new String[imgFiles.length];
          for (int k = 0; k < imgFiles.length; k++) {
            imgList[k] = imgFiles[k].getPath();
          }
        } else {  // handle ./XXXVideoN/
          File[] frameFiles = new File(fileList[i].getPath()).listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
              return !file.isHidden();
            }
          });
          Arrays.sort(frameFiles);
          String[] frameList = new String[frameFiles.length - 1]; // one for .wav
          int cnt = 0;
          for (int j = 0; j < frameFiles.length; j++) {
            String fileName = frameFiles[j].getName();
            if (fileName.substring(fileName.lastIndexOf(".")).equals(".wav")) {
              audioList.add(frameFiles[j].getPath());
            } else {
              frameList[cnt] = frameFiles[j].getPath();
              cnt++;
            }
          }
          frameFolderList.add(frameList);
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

  public void storeSynopsisWithoutMetadata()
  {
    try {
      File outFile = new File("synopsis_without_meta_data.png");
      ImageIO.write(outImg.img, "png", outFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
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

  public void cvtImgBufferToMat(BufferedImage img, Mat imgMat)
  {
    int[] dataInt = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    byte[] dataByte = new byte[img.getWidth() * img.getHeight()*(int)imgMat.elemSize()];
    for (int i = 0; i < dataInt.length; i++) {
      dataByte[i*3] = (byte)((dataInt[i] >> 0) & 0xff);
      dataByte[i*3+1] = (byte)((dataInt[i] >> 8) & 0xff);
      dataByte[i*3+2] = (byte)((dataInt[i] >> 16) & 0xff);
    }
    imgMat.put(0, 0, dataByte);
  }

  public double getLaplacianVar(Mat imgMat)
  {
    Mat imgGray = new Mat();
    Imgproc.cvtColor(imgMat, imgGray, Imgproc.COLOR_BGR2GRAY);
    Mat imgLap = new Mat();
    Imgproc.Laplacian(imgGray, imgLap, CvType.CV_16S);
    MatOfDouble median = new MatOfDouble();
    MatOfDouble std = new MatOfDouble();
    Core.meanStdDev(imgLap, median, std);
    double var = Math.pow(std.get(0,0)[0], 2);
    return var;
  }

  public String[] chooseClearestImgTop10(String[] frameList)
  {
    TreeMap<String, Double> nameVarMap = new TreeMap<String, Double>();
    String[] top10List = new String[10];
    for (int i = 0; i < frameList.length; i++) {
      BufferedImage img = new BufferedImage(iw, iw, BufferedImage.TYPE_INT_RGB);
      readImageRGB(iw, ih, frameList[i], img);
      Mat imgMat = new Mat(ih, iw, CvType.CV_8UC3);
      cvtImgBufferToMat(img, imgMat);
      double var = getLaplacianVar(imgMat);
      nameVarMap.put(frameList[i], var);
    }
    ArrayList<Map.Entry<String, Double>> list = new ArrayList<Map.Entry<String, Double>>(nameVarMap.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
      public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
        return (o2.getValue()).compareTo(o1.getValue());
      }
    });
    for (int i = 0; i < 10; i++) {
      top10List[i] = list.get(i).getKey();
    }
    return top10List;
  }

  public int detectFaceNum(String frameName)
  {
    BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
    readImageRGB(iw, ih, frameName, img);
    Mat imgMat = new Mat(ih, iw, CvType.CV_8UC3);
    cvtImgBufferToMat(img, imgMat);
    Mat imgGray = new Mat();
    Imgproc.cvtColor(imgMat, imgGray, Imgproc.COLOR_BGR2GRAY);
    CascadeClassifier classifier = new CascadeClassifier();
    classifier.load("lbpcascade_frontalface.xml");
    MatOfRect faces = new MatOfRect();
    classifier.detectMultiScale(imgGray, faces);
    return (int)faces.total();
  }

  public void chooseSynopsisClearestTop10DetectMostFace()
  {
    // choose key video frame
    int synopsisFrameNumEachVideoOrImg = synopsisFrameNum / (frameFolderList.size() + 1);
    for (int i = 0; i < frameFolderList.size(); i++) {
      int videoFrameNum = frameFolderList.get(i).length;
      for (int j = 0; j < synopsisFrameNumEachVideoOrImg; j++) {
        int avgPoint = (int)(videoFrameNum * (j+0.5) / synopsisFrameNumEachVideoOrImg);
        String[] searchFrames = new String[searchRange*2 + 1];
        for (int k = 0; k < 2*searchRange+1; k++) {
          searchFrames[k] = frameFolderList.get(i)[avgPoint + k - searchRange];
        }
        String[] top10List = chooseClearestImgTop10(searchFrames);
        int maxFaceNum = -1;
        String chooseFrame = null;
        for (int k = top10List.length-1; k >= 0; k--) {
          int faceNum = detectFaceNum(top10List[k]);
          if (faceNum >= maxFaceNum) {
            maxFaceNum = faceNum;
            chooseFrame = top10List[k];
          }
        }
        outImg.metaData[i*synopsisFrameNumEachVideoOrImg + j].filePath = chooseFrame;
        outImg.metaData[i*synopsisFrameNumEachVideoOrImg + j].isVideo = true;
        outImg.metaData[i*synopsisFrameNumEachVideoOrImg + j].audioPath = audioList.get(i);
      }
    }
    // choose key image
    int imgNum = imgList.length;
    String[] top10List = chooseClearestImgTop10(imgList);
    TreeMap<String, Integer> frameFaceNumMap = new TreeMap<String, Integer>();
    for (int k = 0; k < top10List.length; k++) {
      int faceNum = detectFaceNum(top10List[k]);
      frameFaceNumMap.put(top10List[k], faceNum);
    }
    ArrayList<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(frameFaceNumMap.entrySet());
    Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
      public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
        return (o2.getValue()).compareTo(o1.getValue());
      }
    });
    String[] chooseImg = new String[synopsisFrameNumEachVideoOrImg];
    for (int j = 0; j < synopsisFrameNumEachVideoOrImg; j++) {
      chooseImg[j] = list.get(j).getKey();
    }
    Arrays.sort(chooseImg);
    for (int j = 0; j < synopsisFrameNumEachVideoOrImg; j++) {
      outImg.metaData[frameFolderList.size()*synopsisFrameNumEachVideoOrImg + j].filePath = chooseImg[j];
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

    System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    //cs.chooseSynopsisNaive();
    cs.chooseSynopsisClearestTop10DetectMostFace();
    cs.encodeOutImgLayoutNaive();
    cs.serializeOutImg();
    cs.storeSynopsisWithoutMetadata();
    //cs.showIm(cs.outImg.img);
    //cs.deserializeOutImg();
  }
}