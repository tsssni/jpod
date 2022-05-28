package jPod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JPanel;
import javax.swing.JFileChooser;
import javax.swing.Timer;

import java.awt.Toolkit;
import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyAPIC;

import javazoom.spi.mpeg.sampled.file.MpegAudioFileReader;

import org.jflac.sound.spi.FlacAudioFileReader;
import org.jflac.sound.spi.Flac2PcmAudioInputStream;

public class jPod {
    public static void main(String args[]) throws Exception {
        jPodFrame frame = new jPodFrame();
        frame.start();
    }
}

class jPodFrame extends JFrame implements MouseListener, MouseMotionListener {
    jPodFrame() {
        super();

    }

    void start() {
        Options.initOptionFile(false);

        setSize(mWidth, mHeight);
        setControllerCenter(); // 设置中间控制器的圆心坐标来计算按钮位置

        setLocationRelativeTo(null); // 框体居中
        setLayout(null);

        addMouseListener(this); // 监听事件用于实现按钮与进度条效果
        addMouseMotionListener(this); // 监听事件用于实现拖拽与放大效果

        initBackground(); // 绘制播放器窗体
        initScreenPage(); // 绘制播放器屏幕部分

        setUndecorated(true);
        setVisible(true);
        setBackground(new Color(0.0f, 0.0f, 0.0f, 0.0f)); // 背景设为透明以实现自定义窗体的效果
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    public void mouseDragged(MouseEvent e) {
        int x = e.getPoint().x - mOldX; // 计算拖拽过程的坐标相对于拖拽开始的位置的坐标
        int y = e.getPoint().y - mOldY;

        int winX = getBounds().x; // 取得当前窗体坐标
        int winY = getBounds().y;
        if (!mScale) {
            setLocation(winX + x, winY + y); // 移动窗体到拖拽的位置
        } else {
            mWidth = mOriWidth + x; // 计算放缩后的新的长与宽
            mHeight = mWidth * 2;
            getLayeredPane().remove(mPlayerPanel); // 窗口所有参数重新设置并重新绘制
            initBackground();

            ScreenPage.setParams(mWidth / 20, mWidth / 20, mWidth - mWidth / 10, mWidth - mWidth / 10, this);
            mCurrPage.setBounds(mWidth / 20, mWidth / 20, mWidth - mWidth / 10, mWidth - mWidth / 10);
            ScreenMenu.setItemHeight();

            setSize(mWidth, mHeight);
            setControllerCenter();

            getLayeredPane().repaint();
        }
    }

    public void mouseMoved(MouseEvent e) {

    }

    public void mousePressed(MouseEvent e) {
        mOldX = e.getX(); // 鼠标按下去时的坐标
        mOldY = e.getY();

        if (Math.sqrt(Math.pow(mOldX - this.getWidth(), 2)
                + Math.pow(mOldY - this.getHeight(), 2)) < mWidth / 10) {
            mScale = true; // 若鼠标在窗体右下角按下则为放缩事件
            return;
        }

        double distToCenter = Math.sqrt(Math.pow(mOldX - mCtrlCenterX, 2) + Math.pow(mOldY - mCtrlCenterY, 2)); // 计算到控制器中心的距离
        double controllerOuterRadius = mRadius - mWidth / 20; // 控制器外半径
        double controllerInnerRadius = mRadius - 9 * mWidth / 40; // 控制器内半径

        double vecX = mOldX - mCtrlCenterX; // 计算从控制器圆心到当前按下的点的向量,并归一化以方便计算角度
        double vecY = mCtrlCenterY - mOldY;

        double vecLen = Math.sqrt((double) (vecX * vecX + vecY * vecY));
        vecX /= vecLen; // 归一化
        vecY /= vecLen;

        double arc = Math.acos(vecX); // 获得弧度
        if (vecY < 0) {
            arc = 2 * Math.PI - arc; // 若向量y坐标小于零则是在PI到2PI范围内
        }

        mAngle = (int) (arc * 360.0 / (2.0 * Math.PI)); // 由弧度计算角度

        if (distToCenter < controllerInnerRadius) { // 小于内半径则按的是中间键
            centerPressed = true;
            getLayeredPane().repaint();
        } else if (distToCenter >= controllerInnerRadius && distToCenter < controllerOuterRadius) { // 大于内半径小于外半径则按的是四周四个键
            if ((mAngle >= 0 && mAngle < 45) || (mAngle >= 315 && mAngle < 360)) { // 右键
                rightPressed = true;
                getLayeredPane().repaint();
            } else if (mAngle >= 45 && mAngle < 135) { // 上键
                upPressed = true;
                getLayeredPane().repaint();
            } else if (mAngle >= 135 && mAngle < 225) { // 左键
                leftPressed = true;
                getLayeredPane().repaint();
            } else if (mAngle >= 225 && mAngle < 315) { // 下键
                downPressed = true;
                getLayeredPane().repaint();
            }
            getLayeredPane().repaint();
        } else if (distToCenter >= controllerOuterRadius && distToCenter < mRadius) { // 若大于外半径且小于整个控制器的半径,则按的是进度条

            if (currSong == null || thread == null) { // 若当前没有歌曲正在播放则返回
                return;
            }

            progressBarPressed = true;

            mAngle = 90 - mAngle; // 进度条从90度位置开始, 故进度条经过的角度为90-mAngle
            if (mAngle < 0) {
                mAngle += 360;
            }
            return;
        }
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseReleased(MouseEvent e) {
        if (mScale) {
            mScale = false; // 放缩结束
        }

        if (progressBarPressed) { // 更新动作条
            progressBarPressed = false;
            TimeActionListener.sec = mAngle * TimeActionListener.total / 360;
            TimeActionListener.progressBar = true;
        }

        if (leftPressed) {
            leftPressed = false;
            getLayeredPane().repaint();
            mCurrPage.handleLeftButton();

        } else if (rightPressed) {
            rightPressed = false;
            getLayeredPane().repaint();
            mCurrPage.handleRightButton();

        } else if (upPressed) {
            upPressed = false;
            getLayeredPane().repaint();
            mCurrPage.handleUpButton();
        } else if (downPressed) {
            downPressed = false;
            getLayeredPane().repaint();
            mCurrPage.handleDownButton();
        } else if (centerPressed) {
            centerPressed = false;
            getLayeredPane().repaint();
            mCurrPage.handleCenterButton();
        }
    }

    public void mouseClicked(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    void setControllerCenter() {
        mCtrlCenterX = mWidth / 2; // 计算控制器圆心坐标
        mCtrlCenterY = mWidth + mWidth / 2;
        mRadius = (mWidth - 2 * mWidth / 10) / 2 + mWidth / 40;
    }

    void initBackground() {
        mPlayerPanel = new PlayerPanel(mWidth, mHeight, this); // 绘制播放器主体部分
        mPlayerPanel.setBounds(0, 0, mWidth, mHeight);
        mPlayerPanel.setOpaque(false);
        getLayeredPane().add(mPlayerPanel, Integer.valueOf(Integer.MIN_VALUE + 1));
    }

    void initScreenPage() {
        ScreenPage.setParams(mWidth / 20, mWidth / 20, mWidth - mWidth / 10, mWidth - mWidth / 10, this); // 绘制播放器屏幕部分
        mCurrPage = new RootScreenMenu(mWidth / 20, mWidth / 20, mWidth - mWidth / 10, mWidth - mWidth / 10, this,
                "根目录");
        getLayeredPane().add(mCurrPage, Integer.valueOf(Integer.MIN_VALUE + 2));
    }

    void setCurrScreenPage(ScreenPage page) {
        getLayeredPane().remove(mCurrPage); // 更新当前屏幕页面
        mCurrPage = page;
        getLayeredPane().add(mCurrPage, Integer.valueOf(Integer.MIN_VALUE + 2));
        mCurrPage.setBounds(mWidth / 20, mWidth / 20, mWidth - mWidth / 10, mWidth - mWidth / 10);
        mCurrPage.repaint();
    }

    private int mWidth = 200;
    private int mHeight = 400;
    private int mOriWidth = 200;

    private boolean mScale = false;
    private int mOldX;
    private int mOldY;

    private int mCtrlCenterX;
    private int mCtrlCenterY;
    private int mRadius;
    private int mAngle;

    private PlayerPanel mPlayerPanel;
    private ScreenPage mCurrPage;

    boolean leftPressed = false;
    boolean rightPressed = false;
    boolean upPressed = false;
    boolean downPressed = false;
    boolean centerPressed = false;
    boolean progressBarPressed = false;

    boolean pause = true;
    boolean stop = false;

    Song currSong;
    MusicPlayThread thread;
}

class GBKStringUtil {
    static String GBKString(String str) { // 读取GBK编码的字符串,否则会出现乱码
        if (str == null) {
            return null;
        }

        try {
            return new String(str.getBytes(), Options.encoding);
        } catch (UnsupportedEncodingException e) {
            JOptionPane.showMessageDialog(ScreenPage.sFrame, "UnsupportedEncodingException in GBKStringUtil.GBKString");
            return null;
        }
    }
}

class MusicPlayThread extends Thread {
    MusicPlayThread(jPodFrame frame) {
        mFrame = frame;
    }

    public void run() { // 用于播放音乐的线程
        mTimer = new Timer(1000, new TimeActionListener(mFrame.currSong.duration)); // 用于计算播放时间的计时器
        

        try {
            File file = new File(mFrame.currSong.musicPath);
            if (file.length() >= 40 * 1024 * 1024) {
                throw new SongFileExceedMaximumException();
            }

            AudioInputStream stream = null;
            Clip audioClip = null;
            if (mFrame.currSong.musicPath.endsWith(".mp3")) {
                stream = new MpegAudioFileReader().getAudioInputStream(file);
                AudioFormat baseFormat = stream.getFormat();

                AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate(), 16,
                        baseFormat.getChannels(), baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false);
                stream = AudioSystem.getAudioInputStream(format, stream);
                DataLine.Info dinfo = new DataLine.Info(Clip.class, stream.getFormat(), AudioSystem.NOT_SPECIFIED);

                audioClip = (Clip) AudioSystem.getLine(dinfo);
                audioClip.open(stream);
            } else if (mFrame.currSong.musicPath.endsWith(".flac")) {
                stream = new FlacAudioFileReader().getAudioInputStream(file);
                AudioFormat baseFormat = stream.getFormat();

                AudioFormat format = stream.getFormat();
                if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                    format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate(), 16,
                            baseFormat.getChannels(), baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false);
                }

                Flac2PcmAudioInputStream flacStream = new Flac2PcmAudioInputStream(stream, format, file.length());
                DataLine.Info dinfo = new DataLine.Info(Clip.class, flacStream.getFormat(), AudioSystem.NOT_SPECIFIED);

                audioClip = (Clip) AudioSystem.getLine(dinfo);
                audioClip.open(flacStream);
            }

            mTimer.start();

            while (!mFrame.stop) {
                if (TimeActionListener.progressBar) {
                    audioClip.stop();
                    audioClip.setMicrosecondPosition((long) TimeActionListener.sec * 1000000); // 若进度条被点击则跳转到被选中的位置

                    while (TimeActionListener.progressBar) { // 等待计时器线程处理完成
                        Thread.sleep(10);
                        continue;
                    }
                }

                if (mFrame.pause) {
                    audioClip.stop();
                    Thread.sleep(10);
                    continue; // 若当前是暂停状态,则进程睡眠,不断循环
                } else {
                    audioClip.start();
                }
            }

            audioClip.flush();
            audioClip.close();
            stream.close();       

        } catch(SongFileExceedMaximumException e){
            JOptionPane.showMessageDialog(ScreenPage.sFrame, "歌曲文件超过40MB,无法播放");
        } catch (UnsupportedAudioFileException e) {
            JOptionPane.showMessageDialog(ScreenPage.sFrame, "UnsupportedAudioFileException in MusicPlayThread.run");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(ScreenPage.sFrame, "IOException in MusicPlayThread.run");
        } catch (LineUnavailableException e) {
            JOptionPane.showMessageDialog(ScreenPage.sFrame, "LineUnavailableException in MusicPlayThread.run");
        } catch (InterruptedException e) {
            JOptionPane.showMessageDialog(ScreenPage.sFrame, "InterruptedException in MusicPlayThread.run");
        } finally {
            mTimer.stop();
            mFrame.stop = false;
        }

    }

    private jPodFrame mFrame;
    private Timer mTimer;
}

class TimeActionListener implements ActionListener {
    TimeActionListener(int tot) {
        sec = 0;
        total = tot;
    }

    public void actionPerformed(ActionEvent e) {
        if (ScreenPage.sFrame.pause) {
            if (progressBar) {
                ScreenPage.sFrame.getLayeredPane().repaint(); // 重新绘制进度条
                progressBar = false;
            }

            return;
        }

        if (!progressBar) {
            sec += 1; // 正常状态进度条每一秒更新一次
        } else {
            progressBar = false;
            try {
                Thread.sleep(10); // 线程睡眠来让音乐播放器线程完成工作
            } catch (InterruptedException ex) {
                JOptionPane.showMessageDialog(ScreenPage.sFrame, "InterruptedException in TimeActionListener(first)");
            }
        }

        if (ScreenPage.sFrame.stop) {
            try {
                Thread.sleep(10); // 线程睡眠来等待当前歌曲完成播放
            } catch (InterruptedException ex) {
                JOptionPane.showMessageDialog(ScreenPage.sFrame, "InterruptedException in TimeActionListener(second)");
            }
        }

        ScreenPage.sFrame.getLayeredPane().repaint();
    }

    static int sec = 0;
    static int total = 1;

    static boolean progressBar = false;
}

class PlayerPanel extends JPanel {
    PlayerPanel(int frameWidth, int frameHeight, jPodFrame frame) {
        mFrameWidth = frameWidth;
        mFrameHeight = frameHeight;
        mScreenEdge = mFrameWidth / 20; // 屏幕两侧的空档大小
        mCircleEdge = mFrameWidth / 10; // 控制器的包围矩形相对两侧的距离
        mDiameter = mFrameWidth - 2 * mCircleEdge; // 控制器圆盘直径
        mFrame = frame;
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Options.playerBodyColor);

        g2d.clipRect(0, 0, mScreenEdge, mFrameHeight); // 屏幕上下左右四个方向分别设置clip空间来达到中间挖空的效果
        g2d.fillRect(0, 0, mFrameWidth, mFrameHeight);
        g2d.setClip(null);

        g2d.clipRect(0, 0, mFrameWidth, mScreenEdge);
        g2d.fillRect(0, 0, mFrameWidth, mFrameHeight);
        g2d.setClip(null);

        g2d.clipRect(mFrameWidth - mScreenEdge, 0, mScreenEdge, mFrameHeight);
        g2d.fillRect(0, 0, mFrameWidth, mFrameHeight);
        g2d.setClip(null);

        g2d.clipRect(0, mFrameWidth - mScreenEdge, mFrameWidth, mFrameWidth + mScreenEdge);
        g2d.fillRect(0, 0, mFrameWidth, mFrameHeight);
        g2d.setClip(null);

        g2d.setColor(Options.controllerColor); // 绘制控制圆盘
        g2d.fillRoundRect(mCircleEdge, mFrameWidth + mCircleEdge, mDiameter, mDiameter, mDiameter, mDiameter);

        int progressBarWidth = mDiameter / 20;
        g2d.setStroke(new BasicStroke(progressBarWidth));
        g2d.setColor(Options.progressBarInactivatedColor);

        int playTime = TimeActionListener.sec;
        int totalTime = TimeActionListener.total;

        int play = 360 * playTime / totalTime;
        int angle = 360 - play;

        g2d.drawArc(mCircleEdge, mFrameWidth + mCircleEdge, mDiameter, mDiameter, 90, angle); // 绘制已经被播放的进度条

        g2d.setColor(Options.progressBarActivatedColor);
        g2d.drawArc(mCircleEdge, mFrameWidth + mCircleEdge, mDiameter, mDiameter, 90 + angle, play); // 绘制未被播放的进度条

        if (mFrame.leftPressed || mFrame.rightPressed
                || mFrame.upPressed || mFrame.downPressed || mFrame.centerPressed) {
            int red = Options.controllerColor.getRed() - 5; // 被按下去之后的颜色相比原来的颜色会更深
            int green = Options.controllerColor.getGreen() - 5;
            int blue = Options.controllerColor.getBlue() - 5;
            int alpha = Options.controllerColor.getAlpha();
            Color pressColor = new Color(red, green, blue, alpha);

            g2d.setColor(pressColor);
            int edgeButtonWidth = 4 * progressBarWidth; // 按键的宽度
            int edgeButtonEdge = mCircleEdge + progressBarWidth / 2 + edgeButtonWidth / 2; // 按键所在的圆的包围矩形的左上角坐标
            g2d.setStroke(new BasicStroke(edgeButtonWidth));

            if (mFrame.leftPressed) {
                g2d.drawArc(edgeButtonEdge, mFrameWidth + edgeButtonEdge,
                        mDiameter - progressBarWidth - edgeButtonWidth,
                        mDiameter - progressBarWidth - edgeButtonWidth, 135, 90);
            } else if (mFrame.rightPressed) {
                g2d.drawArc(edgeButtonEdge, mFrameWidth + edgeButtonEdge,
                        mDiameter - progressBarWidth - edgeButtonWidth,
                        mDiameter - progressBarWidth - edgeButtonWidth, 315, 90);
            } else if (mFrame.upPressed) {
                g2d.drawArc(edgeButtonEdge, mFrameWidth + edgeButtonEdge,
                        mDiameter - progressBarWidth - edgeButtonWidth,
                        mDiameter - progressBarWidth - edgeButtonWidth, 45, 90);
            } else if (mFrame.downPressed) {
                g2d.drawArc(edgeButtonEdge, mFrameWidth + edgeButtonEdge,
                        mDiameter - progressBarWidth - edgeButtonWidth,
                        mDiameter - progressBarWidth - edgeButtonWidth, 225, 90);
            } else if (mFrame.centerPressed) {
                g2d.fillRoundRect(edgeButtonEdge + edgeButtonWidth / 2,
                        mFrameWidth + edgeButtonEdge + edgeButtonWidth / 2,
                        mDiameter - progressBarWidth - 2 * edgeButtonWidth,
                        mDiameter - progressBarWidth - 2 * edgeButtonWidth,
                        mDiameter - progressBarWidth - 2 * edgeButtonWidth,
                        mDiameter - progressBarWidth - 2 * edgeButtonWidth);
            }
        }

        g2d.dispose();
    }

    private int mFrameWidth;
    private int mFrameHeight;
    private int mScreenEdge;
    private int mCircleEdge;
    private int mDiameter;

    private jPodFrame mFrame;
}

abstract class ScreenPage extends JPanel {
    ScreenPage(ScreenPage father, String inName) {
        mFatherPage = father;
        name = inName;

        setBounds(sLeft, sUp, sWidth, sHeight); // 设置屏幕位置与大小
    }

    static void setParams(int left, int up, int width, int height, jPodFrame frame) {
        sLeft = left; // 屏幕的x坐标
        sUp = up; // 屏幕的y坐标
        sWidth = width; // 宽度
        sHeight = height; // 高度
        sFrame = frame; // 屏幕所在的窗体
        setFont();
    }

    static void setFont() {
        sFont = new Font(Options.fontName, Font.PLAIN, sWidth / 12); // 设置字体
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(Options.screenBackgroundColor); // 绘制背景色
        g2d.fillRect(0, 0, sWidth, sHeight);
        g2d.dispose();
    }

    protected void handleLeftButton() {

    }

    protected void handleRightButton() {

    }

    protected void handleUpButton() {

    }

    protected void handleDownButton() {

    }

    protected void handleCenterButton() {
        if (sFrame.currSong == null) {
            return;
        }

        sFrame.stop = false;

        if (sFrame.pause) {
            sFrame.pause = false; // 设置该参数后歌曲播放线程和计时器线程会自动处理暂停

            if (sFrame.thread == null || !sFrame.thread.isAlive()) { // 若还未播放歌曲或上一首已经被播放完, 则按下中间键后会创建新线程
                sFrame.thread = new MusicPlayThread(sFrame);
                sFrame.thread.start();
            }

        } else {
            sFrame.pause = true;
            try {
                Thread.sleep(100); // 进程暂停来等待歌曲线程与计时器线程处理完成
            } catch (Exception e) {

            }

        }

    }

    protected void handleChangeSong() {
        if (sFrame.pause) {
            return; // 若当前是暂停状态,则切换歌曲后不会播放
        }

        if (sFrame.thread.isAlive()) { // 等待上一首歌播放结束
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                JOptionPane.showMessageDialog(ScreenPage.sFrame, "InterruptedException in ScreenPage.handleChangeSong");
            }
        }

        if (!sFrame.pause) { // 建立新的歌曲播放线程
            sFrame.thread = new MusicPlayThread(sFrame);
            sFrame.thread.start();

        }
    }

    static int sLeft = 0;
    static int sUp = 0;
    static int sWidth = 0;
    static int sHeight = 0;
    static jPodFrame sFrame = null;
    static Font sFont = null;

    protected ScreenPage mFatherPage;
    protected String name;
}

abstract class ScreenMenu extends ScreenPage {
    ScreenMenu(ScreenPage father, String name) {
        super(father, name);
        setItemHeight(); // 设置每个选项所在的部分的高度
    }

    static void setItemHeight() {
        sItemHeight = sHeight / sPageMaxItems;
    }

    public void paint(Graphics g) {
        super.paint(g);

        Graphics2D g2d = (Graphics2D) g.create();

        g2d.setColor(Options.screenForegroundColor); // 绘制当前被选中的项
        g2d.fillRect(0, mCursorPlace * sItemHeight, sWidth, sItemHeight);

        g2d.setColor(Options.charForegroundColor);

        ArrayList<String> itemArr = new ArrayList<String>();

        for (int i = 0; i < mPageArray.size(); ++i) {
            itemArr.add(mPageArray.get(i).name);
        }

        paintAccordingToLength(g2d, itemArr);
    }

    protected void paintAccordingToLength(Graphics2D g2d, ArrayList<String> itemArr) {
        g2d.setColor(Options.charForegroundColor);
        int lineWidth = g2d.getFontMetrics(sFont).stringWidth(itemArr.get(mCurrItem)); // 根据字符串长度调整字体大小
        int oriSize = sFont.getSize();

        if (lineWidth > sWidth) {
            int i = 0;
            while (lineWidth > sWidth) {
                sFont = new Font(Options.fontName, Font.PLAIN, oriSize - (++i));
                lineWidth = g2d.getFontMetrics(sFont).stringWidth(itemArr.get(mCurrItem));
            }
        }

        g2d.setFont(sFont);
        g2d.drawString(itemArr.get(mCurrItem), 0, sFont.getSize() + mCursorPlace * sItemHeight);

        sFont = new Font(Options.fontName, Font.PLAIN, oriSize);
        g2d.setFont(sFont);

        for (int i = 0; i < sPageMaxItems && i < itemArr.size(); ++i) {
            if (i == mCursorPlace) { // 绘制未被选中的项目, 若循环到当前被选中的项目则跳过
                continue;
            }

            g2d.setColor(Options.charBackgroundColor);

            lineWidth = g2d.getFontMetrics(sFont).stringWidth(itemArr.get(mHeadItem + i));
            if (lineWidth > sWidth) {
                int j = 0;
                while (lineWidth > sWidth) {
                    sFont = new Font(Options.fontName, Font.PLAIN, oriSize - (++j));
                    lineWidth = g2d.getFontMetrics(sFont).stringWidth(itemArr.get(mHeadItem + j));
                }
                g2d.setFont(sFont);
            }

            g2d.drawString(itemArr.get(mHeadItem + i), 0, sFont.getSize() + i * sItemHeight);

            sFont = new Font(Options.fontName, Font.PLAIN, oriSize);
            g2d.setFont(sFont);
        }

        g2d.dispose();
    }

    protected void handleUpButton() {
        if (mCurrItem == 0) { // 到达最顶端,返回
            return;
        }

        mLastPage = false;
        mCurrItem -= 1;
        mCursorPlace -= 1;

        if (mCursorPlace < 0) { // 翻页,调整屏幕上第一个项目的下标
            mCursorPlace = 0;
            mLastPage = true;
            mHeadItem -= 1;
        }

        sFrame.getLayeredPane().repaint();
    }

    protected void handleDownButton() {
        if (mCurrItem == mPageArray.size() - 1) { // 达到尾部则返回
            return;
        }

        mNextPage = false;
        mCurrItem += 1;
        mCursorPlace += 1;

        if (mCursorPlace == sPageMaxItems) { // 翻页,调整屏幕上第一个项目
            mCursorPlace = sPageMaxItems - 1;
            mNextPage = true;
            mHeadItem += 1;
        }

        sFrame.getLayeredPane().repaint();
    }

    protected void handleRightButton() {
        sFrame.setCurrScreenPage(mPageArray.get(mCurrItem));
    }

    protected void handleLeftButton() {
        sFrame.setCurrScreenPage(mFatherPage);
    }

    ArrayList<ScreenPage> mPageArray = new ArrayList<ScreenPage>();

    static int sItemHeight;
    static int sPageMaxItems = 8;

    protected int mHeadItem = 0;
    protected int mCurrItem = 0;
    protected int mCursorPlace = 0;

    protected boolean mLastPage = false;
    protected boolean mNextPage = false;
    protected int mNumOfDefaultPages = 0;
}

class NullPage extends ScreenPage {
    NullPage(ScreenPage father, String name) {
        super(father, name);
    }
}

class DeleteScreenMenu extends ScreenMenu {
    DeleteScreenMenu(ScreenPage father, String name, ArrayList<ScreenPage> arr, int numLastPageDefault) {
        super(father, name);

        mNumLastPageDefault = numLastPageDefault; // 上一个页面中不需要在删除页面中显示的页面的数量
        mNumOfDefaultPages = 1; // 第一个页面为"请选择删除的项",是无效页

        mPageArray = arr;
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        g2d.setColor(Options.screenBackgroundColor);
        g2d.fillRect(0, 0, sWidth, sHeight);

        g2d.setColor(Options.screenForegroundColor);
        g2d.fillRect(0, mCursorPlace * sItemHeight, sWidth, sItemHeight);

        ArrayList<String> itemArr = new ArrayList<String>();
        itemArr.add("请选择删除的项目");
        for (int i = 1; i < mPageArray.size() - mNumLastPageDefault + 1; ++i) {
            itemArr.add(mPageArray.get(i + mNumLastPageDefault - 1).name); // 将父页面所有有效选项的名字加入数组
        }

        paintAccordingToLength(g2d, itemArr);
    }

    protected void handleRightButton() {
        if (mCurrItem < mNumOfDefaultPages) { // 若为无效项则返回
            return;
        }
        delPage = mCurrItem - mNumOfDefaultPages; // 记录被删除的项的位置
        sFrame.setCurrScreenPage(mFatherPage);
    }

    protected int mNumLastPageDefault;
    protected int delPage = -1;
}

class RootScreenMenu extends ScreenMenu {
    RootScreenMenu(int left, int up, int width, int height, jPodFrame frame, String name) {
        super(null, name);
        mFatherPage = null;

        mPageArray.add(new SongsScreenMenu(this, "歌曲"));
        mPageArray.add(new OptionsScreenMenu(this, "设置"));
        mPageArray.add(new ExitScreenMenu(this, "退出"));
    }

    protected void handleLeftButton() {
        return;
    }
}

class SongsScreenMenu extends ScreenMenu {
    SongsScreenMenu(ScreenPage father, String name) {
        super(father, name);
        mNumOfDefaultPages = 2;

        mPageArray.add(new NullPage(this, "添加歌单"));
        mDeletePage = new DeleteScreenMenu(this,
                "删除歌单", mPageArray, 2);

        mPageArray.add(mDeletePage);

        String fileList[] = (new File("SongsLists")).list(); // 从文件中读取歌单

        if (fileList == null) {
            return;
        }

        for (int i = 0; i < fileList.length; ++i) {
            SongsList list = null;
            try {
                list = new SongsList(fileList[i], false); // 从文件里调入已有歌单,故不需要处理文件存在错误,由于函数声明会抛出该错误故需要try-catch,设置newList为false后是不会抛出该错误的
            } catch (Exception e) {

            }

            sequentialAdd(list); // 按字典序加入数组
        }
    }

    public void paint(Graphics g) {

        if (mAddSongList != null) {
            addNewList(mAddSongList); // 新建立的歌单在addNewList函数中处理异常
            mAddSongList = null;
        }

        if (mDeletePage.delPage != -1) { // 处理删除页面
            String dirName = "SongsLists" + File.separator
                    + mSongsLists.get(mDeletePage.delPage).songsListName;

            try {
                File dir = new File(dirName); // 直接删除包含文件文件夹会失败, 故需要提取文件出来删除
                File[] subFiles = dir.listFiles();
                if (subFiles.length != 0) {
                    for (File file : subFiles) {
                        Files.delete(Paths.get(file.getPath()));
                    }
                }
                Files.delete(Paths.get(dirName));
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "删除失败!");
                mDeletePage.delPage = -1;
                return;
            }

            mDeletePage.mCursorPlace = 0;
            mDeletePage.mCurrItem = 0;

            mPageArray.remove(mNumOfDefaultPages + mDeletePage.delPage);
            mSongsLists.remove(mDeletePage.delPage);

            mDeletePage.delPage = -1;
        }

        super.paint(g);
    }

    protected void addNewList(String name) {
        SongsList list = null;
        try {
            list = new SongsList(name, true);
        } catch (FileAlreadyExistsException e) {
            JOptionPane.showMessageDialog(this, "歌单已存在");
        }

        sequentialAdd(list);
    }

    protected void sequentialAdd(SongsList list) {
        int i = 0;
        for (i = 0; i < mSongsLists.size()
                && list.songsListName.compareTo(mSongsLists.get(i).songsListName) >= 0; ++i) {
            // 寻找插入位置
        }

        mSongsLists.add(null);// 数组大小+1
        mPageArray.add(null);

        for (int j = mSongsLists.size() - 1; j >= i + 1; --j) {
            mSongsLists.set(j, mSongsLists.get(j - 1));
            mPageArray.set(j + mNumOfDefaultPages, mPageArray.get(j + mNumOfDefaultPages - 1));
        }

        mSongsLists.set(i, list);
        mPageArray.set(i + mNumOfDefaultPages, new SongsListPage(this, list, list.songsListName));
    }

    protected void handleRightButton() {
        if (mCurrItem != 0) { // 第0项为新建选项
            super.handleRightButton();
        } else {
            mAddSongList = JOptionPane.showInputDialog(this, "请输入歌单名");
            sFrame.getLayeredPane().repaint();
        }
    }

    protected ArrayList<SongsList> mSongsLists = new ArrayList<SongsList>();
    protected String mAddSongList = null;
    protected DeleteScreenMenu mDeletePage;
}

class SongsListPage extends ScreenMenu {
    SongsListPage(ScreenPage father, SongsList list, String name) {
        super(father, name);

        mNumOfDefaultPages = 3; // 有三个非常规页面
        mPageArray.add(new NullPage(this, "添加新的歌曲"));

        mDeletePage = new DeleteScreenMenu(this, "删除歌曲", mPageArray, mNumOfDefaultPages);
        mAddLyricsPage = new AddLyricsMenu(this, "添加歌词", mPageArray, mNumOfDefaultPages);
        mPageArray.add(mDeletePage);
        mPageArray.add(mAddLyricsPage);

        songsList = list;

        for (int i = 0; i < songsList.songs.size(); ++i) {
            mPageArray.add(new SongPage(this, songsList.songs.get(i).musicName, songsList.songs.get(i), playList));
        }

        listPlayOption = Options.playOption;
        sortPlayList(); // 歌单播放顺序排序
    }

    public void paint(Graphics g) {
        if (!listPlayOption.equals(Options.playOption)) {
            listPlayOption = Options.playOption;
            sortPlayList();
        }

        if (mAddSongPath != null) {
            sequentialAdd(mAddSongPath);
            mAddSongPath = null;
            sortPlayList();
        }

        if (mDeletePage.delPage != -1) {
            String dirName = songsList.get(mDeletePage.delPage).musicPath;

            try {
                Files.delete(Paths.get(dirName));
            } catch (IOException e) {
                mDeletePage.delPage = -1;
                JOptionPane.showMessageDialog(this, "删除失败!");
                return;
            }

            mDeletePage.mCursorPlace = 0;
            mDeletePage.mCurrItem = 0;

            mPageArray.remove(mNumOfDefaultPages + mDeletePage.delPage);
            songsList.remove(mDeletePage.delPage);

            mDeletePage.delPage = -1;
            sortPlayList();
        }

        super.paint(g);
    }

    protected void sequentialAdd(String file) {
        try {
            file = checkFile(file);
        } catch (FileAlreadyExistsException e) {
            JOptionPane.showMessageDialog(this, "歌曲已存在");
            return;
        } catch (MusicTypeErrorException e) {
            JOptionPane.showMessageDialog(this, "请添加mp3或flac格式的歌曲");
            return;
        }

        int i = 0;
        try {
            i = songsList.sequentialAdd(file); // 返回值为插入位置
        } catch (Exception e) {

        }

        mPageArray.add(null);
        for (int j = mPageArray.size() - 1; j >= i + mNumOfDefaultPages + 1; --j) {
            mPageArray.set(j, mPageArray.get(j - 1));
        }
        mPageArray.set(i + mNumOfDefaultPages,
                new SongPage(this, songsList.get(i).musicName, songsList.get(i), playList));
    }

    protected String checkFile(String file) throws FileAlreadyExistsException, MusicTypeErrorException {
        int lastPointIndex = file.lastIndexOf(".");
        String musicType = file.substring(lastPointIndex + 1, file.length());
        if (!(musicType.equals("mp3") || musicType.equals("flac"))) {
            throw new MusicTypeErrorException();
        }

        String currDir = "SongsLists" + File.separator + songsList.songsListName;
        int lastDirIndex = file.lastIndexOf(File.separator, file.length() - 1);
        String subStr = file.substring(0, lastDirIndex);

        if (subStr.equals(currDir)) { // 若新添加的歌曲文件就在当前文件夹中则抛出异常
            throw new FileAlreadyExistsException(file);
        } else {
            String musicFile = file.substring(lastDirIndex + File.separator.length(), file.length());
            String newFile = currDir + File.separator + musicFile;

            try {
                Files.createFile(Paths.get(newFile));
            } catch (FileAlreadyExistsException e) {
                throw e;
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "IOException in SongsListPage.checkFile");
            }

            try {
                Files.copy(Paths.get(file), Paths.get(newFile), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "IOException in SongsListPage.checkFile:" + "复制音乐文件失败!");
            }

            return newFile;
        }
    }

    protected void sortPlayList() {
        playList.removeAll(playList);

        if (Options.playOption.equals(Options.PlayOptions.SEQUENTIAL)) {
            for (int i = mNumOfDefaultPages; i < mPageArray.size(); ++i) {
                SongPage page = (SongPage) mPageArray.get(i);
                playList.add(page);
                page.setPlayOrder(i - mNumOfDefaultPages);
            }
        } else if (Options.playOption.equals(Options.PlayOptions.RANDOM)) {
            for (int i = mNumOfDefaultPages; i < mPageArray.size(); ++i) {
                SongPage page = (SongPage) mPageArray.get(i);
                playList.add(page);
            }

            Collections.shuffle(playList);

            for (int i = 0; i < playList.size(); ++i) {
                SongPage page = (SongPage) playList.get(i);
                page.setPlayOrder(i);
            }
        }
    }

    protected void handleRightButton() {
        if (mCurrItem != 0) {
            super.handleRightButton();
        } else {
            JFileChooser chooser = new JFileChooser(); // 若为添加歌曲选项则选择文件位置
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.showDialog(new JLabel(), "选择");
            mAddSongPath = chooser.getSelectedFile().getPath();

            sFrame.getLayeredPane().repaint();
        }
    }

    protected String mAddSongPath = null;
    protected DeleteScreenMenu mDeletePage;
    protected AddLyricsMenu mAddLyricsPage;
    protected SongsList songsList;
    protected ArrayList<SongPage> playList = new ArrayList<SongPage>();

    protected Options.PlayOptions listPlayOption;
}

class AddLyricsMenu extends ScreenMenu {
    AddLyricsMenu(SongsListPage father, String name, ArrayList<ScreenPage> arr, int numLastPageDefault) {
        super(father, name);

        mNumLastPageDefault = numLastPageDefault; // 与删除页面同理
        mNumOfDefaultPages = 1;
        listFather = father;

        mPageArray = arr;
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        g2d.setColor(Options.screenBackgroundColor);
        g2d.fillRect(0, 0, sWidth, sHeight);

        g2d.setColor(Options.screenForegroundColor);
        g2d.fillRect(0, mCursorPlace * sItemHeight, sWidth, sItemHeight);

        ArrayList<String> itemArr = new ArrayList<String>();
        itemArr.add("请选择要添加歌词的歌曲");
        for (int i = 1; i < mPageArray.size() - mNumLastPageDefault + 1; ++i) {
            itemArr.add(mPageArray.get(i + mNumLastPageDefault - 1).name);
        }

        paintAccordingToLength(g2d, itemArr);
    }

    protected void handleRightButton() {
        if (mCurrItem < mNumOfDefaultPages) {
            return;
        }
        addLyricPage = mCurrItem - mNumOfDefaultPages;
        String musicPath = listFather.songsList.songs.get(addLyricPage).musicPath;
        String lyricPath = getLyricPath(musicPath);
        File file = new File(lyricPath);
        if (file.exists()) {
            JOptionPane.showMessageDialog(this, "歌词已存在");
            return;
        }

        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.showDialog(new JLabel(), "选择");
            String getLyricPath = chooser.getSelectedFile().getPath();

            checkLyricType(getLyricPath);
            file.createNewFile();
            Files.copy(Paths.get(getLyricPath), Paths.get(lyricPath), StandardCopyOption.REPLACE_EXISTING);

        } catch (LyricTypeException lte) {
            JOptionPane.showMessageDialog(this, "请选择txt文件");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "IOException in AddLyricsMenu.handleRightButton");
        }
    }

    protected String getLyricPath(String path) {
        int lastSep = path.lastIndexOf(File.separator);
        int lastPt = path.lastIndexOf(".");
        String lyricName = path.substring(lastSep + 1, lastPt);
        return "Lyrics" + File.separator + lyricName + ".txt";
    }

    protected void checkLyricType(String path) throws LyricTypeException {
        int lastPt = path.lastIndexOf(".");
        String type = path.substring(lastPt + 1, path.length());
        if (!type.equals("txt")) {
            throw new LyricTypeException();
        }
    }

    protected int mNumLastPageDefault;
    protected int addLyricPage = -1;

    protected SongsListPage listFather;
}

class SongPage extends ScreenPage {
    SongPage(ScreenPage father, String name, Song song, ArrayList<SongPage> playList) {
        super(father, name);

        mSong = song;
        mLyricPage = new LyricPage(father, "Lyric", this, song); // 显示歌词的页面
        mPlayList = playList;
    }

    public void paint(Graphics g) {
        if (sFrame.currSong != null && sFrame.currSong != mSong) {
            sFrame.currSong = mSong;
            sFrame.stop = true;
            TimeActionListener.sec = 0;
            TimeActionListener.total = mSong.duration;

            handleChangeSong();
        } else if (sFrame.currSong == null) {
            sFrame.currSong = mSong;
            TimeActionListener.sec = 0;
            TimeActionListener.total = mSong.duration;
        }

        super.paint(g);

        Graphics2D g2d = (Graphics2D) g.create();

        Image cover = mSong.cover; // 绘制封面
        g2d.drawImage(cover, 3 * sWidth / 20, sWidth / 20, sWidth - 6 * sWidth / 20, sWidth - 6 * sWidth / 20,
                sFrame);

        g2d.setColor(Options.charBackgroundColor);
        int wordWidth;
        int edgeWidth;
        int oriSize = sFont.getSize();

        wordWidth = g2d.getFontMetrics(sFont).stringWidth(mSong.musicName);
        if (wordWidth > sWidth) {
            int i = 0;
            while (wordWidth > sWidth) {
                sFont = new Font(Options.fontName, Font.PLAIN, oriSize - (++i));
                wordWidth = g2d.getFontMetrics(sFont).stringWidth(mSong.musicName);
            }
        }
        edgeWidth = (sWidth - wordWidth) / 2; // 居中显示需要获取两侧宽度

        g2d.setFont(sFont);
        g2d.drawString(mSong.musicName, edgeWidth,
                sWidth - 2 * sFont.getSize());

        sFont = new Font(Options.fontName, Font.PLAIN, oriSize);

        wordWidth = g2d.getFontMetrics(sFont).stringWidth(mSong.artist);
        if (wordWidth > sWidth) {
            int i = 0;
            while (wordWidth > sWidth) {
                sFont = new Font(Options.fontName, Font.PLAIN, oriSize - (++i));
                wordWidth = g2d.getFontMetrics(sFont).stringWidth(mSong.artist);
            }
        }
        edgeWidth = (sWidth - wordWidth) / 2;

        g2d.setFont(sFont);
        g2d.drawString(mSong.artist, edgeWidth,
                sWidth - sFont.getSize() / 2);

        sFont = new Font(Options.fontName, Font.PLAIN, oriSize);

        g2d.dispose();

        if (TimeActionListener.sec >= TimeActionListener.total) {
            handleDownButton();
        }
    }

    protected void setPlayOrder(int order) {
        mPlayOrder = order;
    }

    protected void handleLeftButton() {
        sFrame.setCurrScreenPage(mFatherPage);
    }

    protected void handleRightButton() {
        sFrame.setCurrScreenPage(mLyricPage);
    }

    protected void handleUpButton() {
        if (Options.playOption.equals(Options.PlayOptions.RECURRENT)) { // 单曲循环则直接更新
            sFrame.currSong = mSong;
            sFrame.stop = true;
            TimeActionListener.sec = 0;
            TimeActionListener.total = mSong.duration;

            handleChangeSong();
            sFrame.setCurrScreenPage(this);
            return;
        }

        int newPageIdx = mPlayOrder - 1; // 切换到上一首
        if (newPageIdx < 0) {
            newPageIdx = mPlayList.size() - 1;
        }

        sFrame.setCurrScreenPage(mPlayList.get(newPageIdx));
    }

    protected void handleDownButton() {
        if (Options.playOption.equals(Options.PlayOptions.RECURRENT)) { // 单曲循环则直接更新
            sFrame.currSong = mSong;
            sFrame.stop = true;
            TimeActionListener.sec = 0;
            TimeActionListener.total = mSong.duration;

            handleChangeSong();
            sFrame.setCurrScreenPage(this);
            return;
        }

        int newPageIdx = (mPlayOrder + 1) % mPlayList.size(); // 切换到下一首
        sFrame.setCurrScreenPage(mPlayList.get(newPageIdx));
    }

    protected Song mSong;
    protected LyricPage mLyricPage;

    protected int mPlayOrder;
    protected ArrayList<SongPage> mPlayList;
}

class LyricPage extends ScreenPage {
    LyricPage(ScreenPage father, String name, SongPage songPage, Song song) {
        super(father, name);

        mSongPage = songPage;
        mSong = song;
        sHighlightCursorPlace = ScreenMenu.sPageMaxItems / 2 - 1; // 歌词高亮显示放在屏幕中间

        readLyrics();
    }

    protected void readLyrics() {
        String path;

        int lastSep = mSong.musicPath.lastIndexOf(File.separator);
        int lastPt = mSong.musicPath.lastIndexOf(".");
        String fileName = mSong.musicPath.substring(lastSep + 1, lastPt);
        path = "Lyrics" + File.separator + fileName + ".txt"; // 歌词文件名与歌曲文件名同名

        File file = new File(path);
        if (file.exists()) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                String lyricLine;

                while ((lyricLine = GBKStringUtil.GBKString(reader.readLine())) != null) {
                    int timeSep = lyricLine.indexOf(" ");
                    mTimeList.add(lyricLine.substring(0, timeSep)); // 加入时间轴
                    mSecList.add(timeToInt(lyricLine.substring(0, timeSep))); // 分秒换算成秒并加入时间轴
                    mLyricsList.add(lyricLine.substring(timeSep + 1, lyricLine.length())); // 加入歌词轴
                }

                reader.close();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "IOException while reading lyrics in " + path);
            }

        }
    }

    protected String getTimeString() {
        String playMin = String.format("%02d", TimeActionListener.sec / 60);
        String playSec = String.format("%02d", TimeActionListener.sec % 60);
        String totMin = String.format("%02d", TimeActionListener.total / 60);
        String totSec = String.format("%02d", TimeActionListener.total % 60);

        return playMin + ":" + playSec + "/" + totMin + ":" + totSec;
    }

    protected int timeToInt(String time) {
        int idxSep = time.indexOf(":");
        int min = Integer.parseInt(time.substring(0, idxSep));
        int sec = Integer.parseInt(time.substring(idxSep + 1, time.length()));
        return min * 60 + sec;
    }

    protected int getCurrLyricIndex() { // 遍历搜索当前歌词
        if (TimeActionListener.sec >= mSecList.get(mSecList.size() - 1)) {
            return mSecList.size() - 1;
        }

        for (int i = 0; i < mSecList.size(); ++i) {
            if (TimeActionListener.sec < mSecList.get(i)) {
                return i - 1;
            }
        }

        return -1;
    }

    public void paint(Graphics g) {
        super.paint(g);
        if (mLyricsList.size() == 0) {
            readLyrics();
        }

        Graphics2D g2d = (Graphics2D) g.create();
        int lineWidth;
        int lineEdgeWidth;

        g2d.setColor(Options.screenForegroundColor);
        g2d.fillRect(0, sHighlightCursorPlace * ScreenMenu.sItemHeight,
                ScreenPage.sWidth, ScreenMenu.sItemHeight);

        if (mLyricsList.size() != 0) {
            String lyric = " ";
            int currLyricIdx = getCurrLyricIndex();
            g2d.setColor(Options.charForegroundColor);

            if (currLyricIdx >= 0) {
                lyric = mLyricsList.get(currLyricIdx);
            }

            int oriSize = sFont.getSize(); // 根据歌词长度动态调整大小

            lineWidth = g2d.getFontMetrics(sFont).stringWidth(lyric);
            if (lineWidth > sWidth) {
                int i = 0;
                while (lineWidth > sWidth) {
                    sFont = new Font(Options.fontName, Font.PLAIN, oriSize - (++i));
                    lineWidth = g2d.getFontMetrics(sFont).stringWidth(lyric);
                }
            }

            lineEdgeWidth = (ScreenPage.sWidth - lineWidth) / 2; // 居中显示获取两侧宽度
            g2d.setFont(sFont);
            g2d.drawString(lyric, lineEdgeWidth, sFont.getSize() + sHighlightCursorPlace * ScreenMenu.sItemHeight);
            sFont = new Font(Options.fontName, Font.PLAIN, oriSize);

            g2d.setColor(Options.charBackgroundColor);

            for (int i = -sHighlightCursorPlace; i <= sHighlightCursorPlace && i + currLyricIdx < mLyricsList.size()
                    && i + sHighlightCursorPlace <= ScreenMenu.sPageMaxItems - 1; ++i) {
                if (i == 0 || currLyricIdx + i < 0) {
                    continue;
                }

                lyric = mLyricsList.get(currLyricIdx + i);
                lineWidth = g2d.getFontMetrics(sFont).stringWidth(lyric);

                if (lineWidth > sWidth) {
                    int j = 0;
                    while (lineWidth > sWidth) {
                        sFont = new Font(Options.fontName, Font.PLAIN, oriSize - (++j));
                        lineWidth = g2d.getFontMetrics(sFont).stringWidth(lyric);
                    }
                }

                lineEdgeWidth = (ScreenPage.sWidth - lineWidth) / 2;
                g2d.setFont(sFont);
                g2d.drawString(lyric, lineEdgeWidth,
                        sFont.getSize() + (sHighlightCursorPlace + i) * ScreenMenu.sItemHeight);
                sFont = new Font(Options.fontName, Font.PLAIN, oriSize);
            }
        }

        g2d.setFont(sFont);
        g2d.setColor(Options.charBackgroundColor);
        String time = getTimeString();
        lineWidth = g2d.getFontMetrics(sFont).stringWidth(time);
        lineEdgeWidth = (ScreenPage.sWidth - lineWidth) / 2;

        g2d.drawString(time, lineEdgeWidth, sFont.getSize() + (ScreenMenu.sPageMaxItems - 1) * ScreenMenu.sItemHeight);

        g2d.dispose();

        if (TimeActionListener.sec >= TimeActionListener.total) {
            handleDownButton();
        }
    }

    protected void handleLeftButton() {
        sFrame.setCurrScreenPage(mFatherPage);
    }

    protected void handleRightButton() {
        sFrame.setCurrScreenPage(mSongPage);
    }

    protected void handleUpButton() {
        mSongPage.handleUpButton();
    }

    protected void handleDownButton() {
        mSongPage.handleDownButton();
    }

    protected SongPage mSongPage;
    protected Song mSong;

    protected ArrayList<String> mTimeList = new ArrayList<String>();
    protected ArrayList<Integer> mSecList = new ArrayList<Integer>();
    protected ArrayList<String> mLyricsList = new ArrayList<String>();

    static int sHighlightCursorPlace;
}

class OptionsScreenMenu extends ScreenMenu {
    OptionsScreenMenu(ScreenPage father, String name) {
        super(father, name);

        mPageArray.add(new PlayOptionsPage(this, "播放设置"));
        mPageArray.add(new SkinOptionsPage(this, "皮肤设置"));
        mPageArray.add(new FontOptionsPage(this, "字体设置"));
        mPageArray.add(new NullPage(this, "初始化"));
    }

    protected void handleRightButton() {
        if (mCurrItem == mPageArray.size() - 1) {
            Options.initOptionFile(true);// 初始化
            sFrame.repaint();
        } else {
            super.handleRightButton();
        }
    }
}

class PlayOptionsPage extends ScreenMenu {
    PlayOptionsPage(ScreenPage father, String name) {
        super(father, name);

        mPageArray.add(new NullPage(this, "顺序播放"));
        mPageArray.add(new NullPage(this, "随机播放"));
        mPageArray.add(new NullPage(this, "单曲循环"));
    }

    protected void handleRightButton() {
        Options.setOption(Integer.toString(mCurrItem), Options.OptionsEnum.PLAY);
        JOptionPane.showMessageDialog(this, "播放设置已修改为:" + mPageArray.get(mCurrItem).name);
    }
}

class SkinOptionsPage extends ScreenMenu {
    SkinOptionsPage(ScreenPage father, String name) {
        super(father, name);

        mPageArray.add(new ColorPage(this, "播放器颜色", Options.OptionsEnum.BODY));
        mPageArray.add(new ColorPage(this, "控制轮盘颜色", Options.OptionsEnum.CTRL));
        mPageArray.add(new ColorPage(this, "进度条已播放颜色", Options.OptionsEnum.PBAC));
        mPageArray.add(new ColorPage(this, "进度条未播放颜色", Options.OptionsEnum.PBIAC));
        mPageArray.add(new ColorPage(this, "屏幕背景颜色", Options.OptionsEnum.SBGC));
        mPageArray.add(new ColorPage(this, "屏幕前景颜色", Options.OptionsEnum.SFGC));
        mPageArray.add(new ColorPage(this, "字体背景颜色", Options.OptionsEnum.CBGC));
        mPageArray.add(new ColorPage(this, "字体前景颜色", Options.OptionsEnum.CFGC));
    }
}

class FontOptionsPage extends ScreenMenu {
    FontOptionsPage(ScreenPage father, String name) {
        super(father, name);

        mPageArray.add(new NullPage(this, "自定义字体"));
        mFonts.add(null);

        mPageArray.add(new NullPage(this, "宋体"));
        mFonts.add("宋体");

        mPageArray.add(new NullPage(this, "仿宋"));
        mFonts.add("仿宋");

        mPageArray.add(new NullPage(this, "楷体"));
        mFonts.add("楷体");

        mPageArray.add(new NullPage(this, "黑体"));
        mFonts.add("黑体");
    }

    protected void handleRightButton() {
        if (mCurrItem == 0) {
            String customFont = JOptionPane.showInputDialog("请输入字体名称"); // 输入自定义字体
            mFonts.set(0, customFont);
        }

        String newFont = mFonts.get(mCurrItem);
        Options.setOption(newFont, Options.OptionsEnum.FONT);

        sFrame.repaint();
    }

    protected ArrayList<String> mFonts = new ArrayList<String>();
}

class ColorPage extends ScreenMenu {
    ColorPage(ScreenPage father, String name, Options.OptionsEnum skinColor) {
        super(father, name);
        mSkinColor = skinColor;

        mPageArray.add(new NullPage(this, "自定义颜色"));
        mColors.add("000000");
        mPageArray.add(new NullPage(this, "黑色"));
        mColors.add("000000");
        mPageArray.add(new NullPage(this, "白色"));
        mColors.add("ffffff");
        mPageArray.add(new NullPage(this, "灰色"));
        mColors.add("808a87");
        mPageArray.add(new NullPage(this, "红色"));
        mColors.add("ff0000");
        mPageArray.add(new NullPage(this, "粉红"));
        mColors.add("e799b0");
        mPageArray.add(new NullPage(this, "深红"));
        mColors.add("990033");
        mPageArray.add(new NullPage(this, "绿色"));
        mColors.add("00ff00");
        mPageArray.add(new NullPage(this, "青色"));
        mColors.add("00ffff");
        mPageArray.add(new NullPage(this, "深绿"));
        mColors.add("228b22");
        mPageArray.add(new NullPage(this, "黄色"));
        mColors.add("ffff00");
        mPageArray.add(new NullPage(this, "橙色"));
        mColors.add("ff8000");
        mPageArray.add(new NullPage(this, "橙红色"));
        mColors.add("db7d74");
        mPageArray.add(new NullPage(this, "棕色"));
        mColors.add("802a2a");
        mPageArray.add(new NullPage(this, "蓝色"));
        mColors.add("0000ff");
        mPageArray.add(new NullPage(this, "天蓝色"));
        mColors.add("9ac8e2");
        mPageArray.add(new NullPage(this, "深蓝色"));
        mColors.add("576690");
        mPageArray.add(new NullPage(this, "紫色"));
        mColors.add("8a2be2");
        mPageArray.add(new NullPage(this, "淡紫色"));
        mColors.add("b8a6d9");
    }

    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        g2d.setFont(sFont);

        g2d.setColor(Options.screenBackgroundColor);
        g2d.fillRect(0, 0, sWidth, sHeight);

        g2d.setColor(Options.screenForegroundColor);
        g2d.fillRect(0, mCursorPlace * sItemHeight, sWidth, sItemHeight);

        g2d.setColor(Options.charForegroundColor);
        if (mCurrItem == 0) {
            g2d.drawString(mPageArray.get(mCurrItem).name, 0, sFont.getSize() + mCursorPlace * sItemHeight);
        } else {
            int itemEdge = sItemHeight / 8;
            g2d.drawString(mPageArray.get(mCurrItem).name, sItemHeight + itemEdge,
                    sFont.getSize() + mCursorPlace * sItemHeight);

            g2d.setColor(Options.RGBATrans(mColors.get(mCurrItem)));
            g2d.fillRect(itemEdge, mCursorPlace * sItemHeight + itemEdge, sItemHeight - itemEdge,
                    sItemHeight - itemEdge); // 在颜色名之前画上色块
        }

        g2d.setColor(Options.charBackgroundColor);

        for (int i = 0; i < sPageMaxItems && i < mPageArray.size(); ++i) {
            if (i == mCursorPlace) {
                continue;
            }

            if (mHeadItem + i == 0) {
                g2d.setColor(Options.charBackgroundColor);
                g2d.drawString(mPageArray.get(mHeadItem + i).name, 0, sFont.getSize() + i * sItemHeight);
            } else {
                int itemEdge = sItemHeight / 8;
                g2d.setColor(Options.charBackgroundColor);
                g2d.drawString(mPageArray.get(mHeadItem + i).name, sItemHeight + itemEdge,
                        sFont.getSize() + i * sItemHeight);

                g2d.setColor(Options.RGBATrans(mColors.get(mHeadItem + i)));
                g2d.fillRect(itemEdge, i * sItemHeight + itemEdge, sItemHeight - itemEdge, // 在颜色名之前画上色块
                        sItemHeight - itemEdge);
            }
        }

    }

    protected void handleRightButton() {
        if (mCurrItem == 0) {
            String customColor = JOptionPane.showInputDialog("请输入RGB(A)颜色");
            try {
                checkColor(customColor);
                mColors.set(0, customColor);

            } catch (ColorException e) {
                JOptionPane.showMessageDialog(this, "请输入正确的颜色");
                return;
            }
        }

        String newColor = mColors.get(mCurrItem);
        Options.setOption(newColor, mSkinColor);

        sFrame.repaint();
    }

    void checkColor(String color) throws ColorException { // 检查是否是正确的RGB(A)颜色
        if (color.length() != 6 && color.length() != 8) {
            throw new ColorException();
        }
        for (int i = 0; i < color.length(); ++i) {
            int ascii = (int) color.charAt(i);
            a = (int) 'a';
            f = (int) 'f';
            zero = (int) '0';
            nine = (int) '9';
            if (!((ascii >= a && ascii <= f) || (ascii >= zero && ascii <= nine))) {
                throw new ColorException();
            }
        }
    }

    static int a;
    static int f;
    static int zero;
    static int nine;

    Options.OptionsEnum mSkinColor;
    ArrayList<String> mColors = new ArrayList<String>();
}

class ExitScreenMenu extends ScreenPage {
    ExitScreenMenu(ScreenPage father, String name) {
        super(father, name);
    }

    public void paint(Graphics g) {
        sFrame.stop = true;
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            JOptionPane.showMessageDialog(this, "InterruptedException during Exit");
        }

        sFrame.dispose();
        System.exit(0);
    }
}

class Options {
    static void initOptionFile(boolean reinit) {
        initHexMap();

        File file = new File("options.txt"); // 初始化
        if (!file.exists() || reinit) { // 不存在设置文件则新建文件
            try {
                if (!reinit) {
                    file.createNewFile();
                }

                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(file)));

                writer.write("0");
                writer.write('\n');

                writer.write("宋体");
                writer.write('\n');

                writer.write("cccccc");
                writer.write('\n');

                writer.write("808a87");
                writer.write('\n');

                writer.write("f0ffff80");
                writer.write('\n');

                writer.write("7fff0080");
                writer.write('\n');

                writer.write("ffffff80");
                writer.write('\n');

                writer.write("03a89e80");
                writer.write('\n');

                writer.write("000000");
                writer.write('\n');

                writer.write("ffffff");
                writer.write('\n');

                writer.write("GBK");
                writer.write('\n');

                writer.flush();
                writer.close();
            } catch (Exception e) {
                return;
            }
        }

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file)));
            playOption = PlayOptions.values()[Integer.parseInt(reader.readLine())];
            fontName = reader.readLine();
            ScreenPage.setFont();
            playerBodyColor = RGBATrans(reader.readLine());
            controllerColor = RGBATrans(reader.readLine());
            progressBarActivatedColor = RGBATrans(reader.readLine());
            progressBarInactivatedColor = RGBATrans(reader.readLine());
            screenBackgroundColor = RGBATrans(reader.readLine());
            screenForegroundColor = RGBATrans(reader.readLine());
            charBackgroundColor = RGBATrans(reader.readLine());
            charForegroundColor = RGBATrans(reader.readLine());
            encoding = reader.readLine();
            reader.close();
        } catch (Exception e) {

        }

    }

    static Color RGBATrans(String hexColor) { // 字符串转颜色类
        hexColor.toLowerCase();
        String rStr = hexColor.substring(0, 2);
        String gStr = hexColor.substring(2, 4);
        String bStr = hexColor.substring(4, 6);
        String aStr = "ff";
        if (hexColor.length() > 6) {
            aStr = hexColor.substring(6, 8);
        }

        int r = mapHex.get(rStr.substring(0, 1)) * 16
                + mapHex.get(rStr.substring(1, 2));
        int g = mapHex.get(gStr.substring(0, 1)) * 16
                + mapHex.get(gStr.substring(1, 2));
        int b = mapHex.get(bStr.substring(0, 1)) * 16
                + mapHex.get(bStr.substring(1, 2));
        int a = mapHex.get(aStr.substring(0, 1)) * 16
                + mapHex.get(aStr.substring(1, 2));

        return new Color(r, g, b, a);
    }

    static void initHexMap() { // 建立映射使得字符直接映射到数字
        for (int i = 0; i < 10; ++i) {
            mapHex.put(Integer.toString(i), Integer.valueOf(i));
        }

        int aAscii = (int) 'a';
        for (int i = 0; i < 6; i++) {
            char ch = (char) (aAscii + i);
            mapHex.put(Character.toString(ch), Integer.valueOf(10 + i));
        }
    }

    static void setOption(String Option, OptionsEnum oEnum) {
        try {
            File file = new File("options.txt"); // 将文件中内容全部读入存入数组,修改指定内容后在全部写回

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file)));
            String line;
            ArrayList<String> fileArr = new ArrayList<String>();
            while ((line = reader.readLine()) != null) {
                fileArr.add(line);
            }

            fileArr.set(oEnum.ordinal(), Option);
            reader.close();

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file)));
            for (String str : fileArr) {
                writer.write(str);
                writer.write('\n');
            }

            writer.close();

        } catch (Exception e) {
            return;
        }

        switch (oEnum) {
            case PLAY:
                playOption = PlayOptions.values()[Integer.parseInt(Option)];
                break;
            case FONT:
                fontName = Option;
                ScreenPage.setFont();
                break;
            case BODY:
                playerBodyColor = RGBATrans(Option);
                break;
            case CTRL:
                controllerColor = RGBATrans(Option);
                break;
            case PBAC:
                progressBarActivatedColor = RGBATrans(Option);
                break;
            case PBIAC:
                progressBarInactivatedColor = RGBATrans(Option);
                break;
            case SBGC:
                screenBackgroundColor = RGBATrans(Option);
                break;
            case SFGC:
                screenForegroundColor = RGBATrans(Option);
                break;
            case CBGC:
                charBackgroundColor = RGBATrans(Option);
                break;
            case CFGC:
                charForegroundColor = RGBATrans(Option);
                break;
            case ENCD:
                encoding = Option;
                break;
        }
    }

    static enum PlayOptions {
        SEQUENTIAL, RANDOM, RECURRENT
    }

    static HashMap<String, Integer> mapHex = new HashMap<String, Integer>();

    static PlayOptions playOption;
    static String fontName;
    static Color playerBodyColor;
    static Color controllerColor;
    static Color progressBarActivatedColor;
    static Color progressBarInactivatedColor;
    static Color screenBackgroundColor;
    static Color screenForegroundColor;
    static Color charBackgroundColor;
    static Color charForegroundColor;
    static String encoding = "GBK";

    static enum OptionsEnum {
        PLAY, FONT, BODY, CTRL, PBAC, PBIAC, SBGC, SFGC, CBGC, CFGC, ENCD
    }
}

class SongsList {
    SongsList(String name, boolean newList) throws FileAlreadyExistsException {
        songsListName = name;
        String pathStr = new String("SongsLists" + File.separator + songsListName);
        Path path = Paths.get(pathStr);

        if (newList) {
            try {
                Files.createDirectory(path);
            } catch (FileAlreadyExistsException e) {
                throw e;
            } catch (IOException e) {
                JOptionPane.showMessageDialog(ScreenPage.sFrame, "IOException in SongsList's Constructor");
            }

            return;
        }

        File file = new File(new String(pathStr));
        String fileList[] = file.list();

        for (int i = 0; i < fileList.length; ++i) {
            fileList[i] = "SongsLists" + File.separator + songsListName + File.separator + fileList[i];
            try {
                sequentialAdd(fileList[i]);
            } catch (Exception e) {

            }
        }
    }

    int sequentialAdd(String path) throws Exception {
        Song song = new Song(path, true);

        int i = 0;
        for (i = 0; i < songs.size() && song.musicName.compareTo(songs.get(i).musicName) >= 0; ++i) {

        }

        songs.add(null);
        for (int j = songs.size() - 1; j >= i + 1; j--) {
            songs.set(j, songs.get(j - 1));
        }

        songs.set(i, song);
        return i;
    }

    void remove(int idx) {
        songs.remove(idx);
    }

    Song get(int idx) {
        return songs.get(idx);
    }

    int numSongs() {
        return songs.size();
    }

    String songsListName = new String();
    ArrayList<Song> songs = new ArrayList<Song>();
}

class Song {
    Song(String path, boolean newSong) throws Exception {
        musicPath = path;
        audioFile = AudioFileIO.read(new File(path));
        audioHeader = audioFile.getAudioHeader();

        MusicType type = getMusicType(path);
        if (type.equals(MusicType.MP3)) {
            AbstractID3v2Tag tag = (AbstractID3v2Tag) audioFile.getTag(); // 从tag中读取歌曲信息
            getMp3MusicInfo(tag);
        } else if (type.equals(MusicType.FLAC)) {
            FlacTag tag = (FlacTag) audioFile.getTag();
            getFlacMusicInfo(tag);
        }
    }

    enum MusicType {
        MP3, FLAC
    }

    MusicType getMusicType(String path) {
        int pointIdx = path.lastIndexOf(".");
        String typeStr = path.substring(pointIdx + 1, path.length());
        if (typeStr.equals("mp3")) {
            return MusicType.MP3;
        } else {
            return MusicType.FLAC;
        }
    }

    void getMp3MusicInfo(AbstractID3v2Tag tag) {
        if (tag != null) {
            musicName = tag.getFirst(FieldKey.TITLE);
            artist = tag.getFirst(FieldKey.ARTIST);
            album = tag.getFirst(FieldKey.ALBUM);
            duration = audioHeader.getTrackLength();
            cover = getMp3SongCover(tag);
        }
    }

    void getFlacMusicInfo(FlacTag tag) {
        if (tag != null) {
            musicName = tag.getFirst(FieldKey.TITLE);
            artist = tag.getFirst(FieldKey.ARTIST);
            album = tag.getFirst(FieldKey.ALBUM);
            duration = audioHeader.getTrackLength();
            cover = getFlacSongCover(tag);
        }
    }

    Image getMp3SongCover(AbstractID3v2Tag tag) {
        AbstractID3v2Frame frame = (AbstractID3v2Frame) tag.getFrame("APIC");
        FrameBodyAPIC body;
        if (frame != null && !frame.isEmpty()) {
            body = (FrameBodyAPIC) frame.getBody(); // 按字节读入封面图片
            byte[] imageData = body.getImageData();
            return Toolkit.getDefaultToolkit().createImage(imageData, 0, imageData.length);
        }
        return null;
    }

    Image getFlacSongCover(FlacTag tag) {
        byte[] imageData = tag.getFirstArtwork().getBinaryData();
        if (imageData != null) {
            return Toolkit.getDefaultToolkit().createImage(imageData, 0, imageData.length);
        }
        return null;
    }

    AudioFile audioFile;
    AudioHeader audioHeader;

    String musicPath;
    String musicName;
    String artist;
    String album;
    String lyrics;
    int duration;
    Image cover;
}

class SongFileExceedMaximumException extends Exception {
    
}

class LyricTypeException extends Exception {

}

class MusicTypeErrorException extends Exception {

}

class ColorException extends Exception {

}
