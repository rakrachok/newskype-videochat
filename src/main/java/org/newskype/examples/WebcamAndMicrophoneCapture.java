package org.newskype.examples;

import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WebcamAndMicrophoneCapture {
    final private static int WEBCAM_DEVICE_INDEX = 0;
    final private static int AUDIO_DEVICE_INDEX = 1;

    final private static int FRAME_RATE = 30;

    private static final OpenCVFrameConverter converter = new OpenCVFrameConverter.ToMat();

    public static void main(String[] args) throws Exception {
        int captureWidth = 1280;
        int captureHeight = 720;
        final int channels = 2;

        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(WEBCAM_DEVICE_INDEX);
        grabber.setImageWidth(captureWidth);
        grabber.setImageHeight(captureHeight);
        grabber.start();

        new Thread(() -> {
            // Pick a format...
            // NOTE: It is better to enumerate the formats that the system supports,
            // because getLine() can error out with any particular format...
            // For us: 44.1 sample rate, 16 bits, stereo, signed, little endian
            AudioFormat audioFormat = new AudioFormat(44100.0f, 16, channels, true, false);

            // Get TargetDataLine with that format
            Mixer.Info[] minfoSet = AudioSystem.getMixerInfo();
            Mixer mixer = AudioSystem.getMixer(minfoSet[AUDIO_DEVICE_INDEX]);
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

            try {
                // Open and start capturing audio
                // It's possible to have more control over the chosen audio device with this line:
                final TargetDataLine line = (TargetDataLine) mixer.getLine(dataLineInfo);
                line.open(audioFormat);
                line.start();

                final int sampleRate = (int) audioFormat.getSampleRate();
                final int numChannels = audioFormat.getChannels();

                // Let's initialize our audio buffer...
                int audioBufferSize = sampleRate * numChannels;
                final byte[] audioBytes = new byte[audioBufferSize];

                // Using a ScheduledThreadPoolExecutor vs a while loop with
                // a Thread.sleep will allow
                // us to get around some OS specific timing issues, and keep
                // to a more precise
                // clock as the fixed rate accounts for garbage collection
                // time, etc
                // a similar approach could be used for the webcam capture
                // as well, if you wish
                ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
                exec.scheduleAtFixedRate(() -> {
                    // Read from the line... non-blocking
                    int nBytesRead = 0;
                    while (nBytesRead == 0) {
                        nBytesRead = line.read(audioBytes, 0, line.available());
                    }

                    // Since we specified 16 bits in the AudioFormat,
                    // we need to convert our read byte[] to short[]
                    // (see source from FFmpegFrameRecorder.recordSamples for AV_SAMPLE_FMT_S16)
                    // Let's initialize our short[] array
                    int nSamplesRead = nBytesRead / 2;
                    short[] samples = new short[nSamplesRead];

                    // Let's wrap our short[] into a ShortBuffer and
                    // pass it to recordSamples
                    ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);

                    // recorder is instance of
                    // org.bytedeco.javacv.FFmpegFrameRecorder
                    //                                recorder.recordSamples(sampleRate, numChannels, sBuff);
                }, 0, (long) 1000 / FRAME_RATE, TimeUnit.MILLISECONDS);
            } catch (LineUnavailableException e1) {
                e1.printStackTrace();
            }
        }).start();

        // A really nice hardware accelerated component for our preview...
        CanvasFrame cFrame = new CanvasFrame("Capture Preview", CanvasFrame.getDefaultGamma() / grabber.getGamma());

        Frame capturedFrame;

        // While we are capturing...
        while ((capturedFrame = grabber.grab()) != null) {
            if (cFrame.isVisible()) {
                opencv_core.Mat sourceMat = converter.convertToMat(capturedFrame);
                opencv_core.Mat destMat = new opencv_core.Mat();
                opencv_core.flip(sourceMat, destMat, 1);
                capturedFrame = converter.convert(destMat);
                // Show our frame in the preview
                cFrame.showImage(capturedFrame);
            }
        }

        cFrame.dispose();
        grabber.stop();
    }
}