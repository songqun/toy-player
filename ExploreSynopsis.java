import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.*;
import java.lang.Math.*;
import javax.sound.sampled.*;


public class ExploreSynopsis {
  static int iw = 352;
  static int ih = 288;
  static int synopsisFrameNum = 20;
  static int hFrameNum = 2;
  static int wFrameNum = synopsisFrameNum / hFrameNum;
  static int wSynopsis = (iw/2) * wFrameNum;
  static int hSynopsis = (ih/2) * hFrameNum;
  static int wPlayer = wSynopsis;
  static int hPlayer = ih * 2 + hSynopsis;
  static int fps = 30;
  String synopsisPath;
  BufferedImageWithMetaData synopsisImg;
  ArrayList<BufferedImage[]> playVideoFrame; // store all video frames
  ArrayList<HashMap<String, Integer>> playFrameStringToIdx;
  ArrayList<BufferedImage> playImg; // only store ./Image/RGB/image-xxx in synopsisImg
  HashMap<String, Integer> playImgStringToIdx;
  ArrayList<String> folderNameList;
  JFrame jf;
  JLabel jlVideoImg;
  JLabel jlVideoImgText;
  int currentFrame = 0; // from idx 0, not 1. If click synopsisImg or stop, it will be update
  int currentVideoFolder = -1; // initially -1 means no setup
  String currentAudioPath;
  boolean currentIsVideo = true;
  static int ST_STOP = 0;
  static int ST_PLAY = 1;
  int status = ST_STOP;


  /*
   * Constructor
   */
  public ExploreSynopsis(String synopsisPath)
  {
    this.synopsisPath = synopsisPath;
    this.synopsisImg = new BufferedImageWithMetaData(synopsisFrameNum, wSynopsis, hSynopsis);
    this.playVideoFrame = new ArrayList<BufferedImage[]>();
    this.playFrameStringToIdx = new ArrayList<HashMap<String, Integer>>();
    this.playImg = new ArrayList<BufferedImage>();
    this.playImgStringToIdx = new HashMap<String, Integer>();
    this.folderNameList = new ArrayList<String>();
    this.jf = new JFrame();
    this.jlVideoImg = new JLabel();
    this.jlVideoImg.setHorizontalAlignment(JLabel.CENTER);
    this.jlVideoImg.setVerticalAlignment(JLabel.CENTER);
    this.jlVideoImgText = new JLabel();
    this.jlVideoImgText.setHorizontalAlignment(JLabel.CENTER);
    this.jlVideoImgText.setVerticalAlignment(JLabel.CENTER);
    this.currentAudioPath = new String();
  }

  public void deserializeSynopsisImg()
  {
    try {
      File file = new File(synopsisPath);
      ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
      synopsisImg.readObject(ois);
      ois.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void loadImgInFolder()
  {
    // init playVideoFrame for multiple folder
    int playImgIdx = 0;
    for (int i = 0; i < synopsisFrameNum; i++) {
      if (synopsisImg.metaData[i].isVideo) {
        String parentPath = new File(synopsisImg.metaData[i].filePath).getParent();
        if (!folderNameList.contains(parentPath)) {
          folderNameList.add(parentPath);
        }
      } else {
        BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        readImageRGB(iw, ih, synopsisImg.metaData[i].filePath, img);
        playImg.add(img);
        playImgStringToIdx.put(synopsisImg.metaData[i].filePath, playImgIdx);
        playImgIdx++;
      }
    }
    // read all frames in video folders, use multi-thread like omp to optimize reading
    for (int i = 0; i < folderNameList.size(); i++) {
      File[] framesList = new File(folderNameList.get(i)).listFiles();
      Arrays.sort(framesList);
      BufferedImage[] frames = new BufferedImage[framesList.length];
      HashMap<String, Integer> frameStringToIdx = new HashMap<String, Integer>();
      // omp optimize
      for (int j = 0; j < framesList.length; j++) {
        frameStringToIdx.put(framesList[j].getPath(), j);
        BufferedImage frame = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        readImageRGB(iw, ih, framesList[j].getPath(), frame);
        frames[j] = frame;
      }
      playFrameStringToIdx.add(frameStringToIdx);
      playVideoFrame.add(frames);
    }
  }

  public void showJFrame()
  {
    jf.setSize(wPlayer, hPlayer);
    jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    jf.setLayout(new BorderLayout());
    JButton play = new JButton("Play");
    JButton pause = new JButton("Pause");
    JButton stop = new JButton("Stop");
    JButton back1s = new JButton("Back 1s");
    JButton forward1s = new JButton("Forward 1s");
    JPanel jpPlayer = new JPanel(new BorderLayout());
    jpPlayer.add(jlVideoImgText, BorderLayout.NORTH);
    jpPlayer.add(jlVideoImg, BorderLayout.CENTER);
    JPanel jpButton = new JPanel();
    jpButton.add(play);
    jpButton.add(pause);
    jpButton.add(stop);
    jpButton.add(back1s);
    jpButton.add(forward1s);
    jpPlayer.add(jpButton, BorderLayout.SOUTH);
    jf.add(jpPlayer);
    JLabel jlSynopsisImg = new JLabel(new ImageIcon(synopsisImg.img));
    jf.add(jlSynopsisImg, BorderLayout.SOUTH);
    jf.addMouseListener(
      new MouseListener() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (e.getButton() == MouseEvent.BUTTON1) {
            int relativeX = e.getX() - (jf.getWidth() - wSynopsis) / 2;
            int relativeY = e.getY() - jf.getHeight() + hSynopsis;
            for (int i = 0; i < synopsisFrameNum; i++) {
              MetaData md = synopsisImg.metaData[i];
              if (relativeX >= md.wLower && relativeX < md.wUpper && 
                relativeY >= md.hLower && relativeY < md.hUpper) {
                  if (md.isVideo) {
                    status = ST_STOP;
                    currentIsVideo = true;
                    currentVideoFolder = folderNameList.indexOf(new File(md.filePath).getParent());
                    currentFrame = playFrameStringToIdx.get(currentVideoFolder).get(md.filePath);
                    jlVideoImg.setIcon(new ImageIcon(playVideoFrame.get(currentVideoFolder)[currentFrame]));
                    jlVideoImgText.setText(new File(md.filePath).getParent());
                    currentAudioPath = md.audioPath;
                  } else {
                    status = ST_STOP;
                    currentIsVideo = false;
                    jlVideoImg.setIcon(new ImageIcon(playImg.get(playImgStringToIdx.get(md.filePath))));
                    jlVideoImgText.setText(md.filePath);
                  }
                  break;
              }
            }
          }
        }
        @Override
        public void mouseReleased(MouseEvent e) {}
        @Override
        public void mouseExited(MouseEvent e) {}
        @Override
        public void mouseEntered(MouseEvent e) {}
        @Override
        public void mouseClicked(MouseEvent e) {}
      }
    );
    play.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (status != ST_PLAY && currentIsVideo && currentVideoFolder > -1) {
            if (currentFrame == playVideoFrame.get(currentVideoFolder).length) {
              currentFrame = 0;
            }
            status = ST_PLAY;
            new Thread(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    AudioInputStream ais = AudioSystem.getAudioInputStream(new File(currentAudioPath));
                    AudioFormat af = ais.getFormat();
                    DataLine.Info info = new DataLine.Info(Clip.class, af);
                    Clip clip = (Clip) AudioSystem.getLine(info);
                    clip.open(ais);
                    clip.setMicrosecondPosition(currentFrame / fps * 1000000);
                    clip.start();
                    showIms();
                    clip.stop();
                  } catch (UnsupportedAudioFileException e) {
                    e.printStackTrace();
                  } catch (IOException e) {
                    e.printStackTrace();
                  } catch (LineUnavailableException e) {
                    e.printStackTrace();
                  }
                }
              }
            ).start();
          }
        }
      }
    );
    pause.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (status != ST_STOP && currentIsVideo) {
            status = ST_STOP;
          }
        }
      }
    );
    stop.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (currentIsVideo) {
            currentFrame = 0;
            status = ST_STOP;
            jlVideoImg.setIcon(new ImageIcon(playVideoFrame.get(currentVideoFolder)[currentFrame]));
          }
        }
      }
    );
    back1s.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (currentIsVideo) {
            currentFrame = Math.max(0, currentFrame - 30);
            status = ST_STOP;
            jlVideoImg.setIcon(new ImageIcon(playVideoFrame.get(currentVideoFolder)[currentFrame]));
          }
        }
      }
    );
    forward1s.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (currentIsVideo) {
            currentFrame = Math.min(playVideoFrame.get(currentVideoFolder).length-1, currentFrame + 30);
            status = ST_STOP;
            jlVideoImg.setIcon(new ImageIcon(playVideoFrame.get(currentVideoFolder)[currentFrame]));
          }
        }
      }
    );
    jf.setVisible(true);
  }

  public void showIms()
  {
    while (currentFrame < playVideoFrame.get(currentVideoFolder).length && status == ST_PLAY) {
      jlVideoImg.setIcon(new ImageIcon(playVideoFrame.get(currentVideoFolder)[currentFrame]));
      currentFrame++;
      try {
        Thread.sleep((long)(1000/30));
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    status = ST_STOP; // for reaching video end
  }

  /*
   * Show img, for debug
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

  /*
   * Read Image RGB
   */
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
    if (args.length != 1) {
      System.err.println("Arguments format error. It should be: synopsisPath.");
      System.exit(1);
    }
    String synopsisPath = args[0];
    ExploreSynopsis es = new ExploreSynopsis(synopsisPath);
    es.deserializeSynopsisImg();
    es.loadImgInFolder();
    es.showJFrame();
  }
}