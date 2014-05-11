package com.openresearchinc.hadoop.sequencefile;

import static com.googlecode.javacv.cpp.opencv_core.cvClearMemStorage;
import static com.googlecode.javacv.cpp.opencv_core.cvGetSeqElem;
import static com.googlecode.javacv.cpp.opencv_core.cvLoad;
import static com.googlecode.javacv.cpp.opencv_objdetect.CV_HAAR_DO_CANNY_PRUNING;
import static com.googlecode.javacv.cpp.opencv_objdetect.cvHaarDetectObjects;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.GenericOptionsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvRect;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_objdetect.CvHaarClassifierCascade;

//@formatter:off
/**
 * (Deprecated) OpenCV cp <opencv-path>/release/lib/libopencv_java248.so
 * <hadoop-path>/lib/native/. otherwise will get following problem
 * java.lang.UnsatisfiedLinkError: no opencv_java248 in java.library.path
 * 
 * JavaCV: 1. download and extract both javacv-0.7-bin.zip
 * andjavacv-0.7-cppjars.zip 2. In Eclipse: create a user library JavaCV
 * containing 1)javacv.jar 2)javacpp.jar 3) javacv-<plaform>-<os>.jar 4)
 * ffmpeg-<ver>-<platform>.jar 5) opencv-<ver>-<platform>.jar
 * 
 * @author heq
 */
// @formatter:on
public class OpenCV {
	private static final Logger logger = LoggerFactory.getLogger(OpenCV.class);
	static String hostname = "localhost";
	static {
		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			logger.warn("hostname is NOT resolvable by DNS. localhost is used for logging");
		}
	}
	private static final Configuration conf = new Configuration();

	public static void main(String[] args) throws Exception {
		String usage = "Usage: hadoop jar ./target/*.jar com.openresearchinc.hadoop.sequencefile.OpenCV [-file <input-uri-image>|-dir <input-uri-images>] -ext <ext> ";
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();

		List<String> argList = Arrays.asList(otherArgs);
		String uri;
		if (argList.indexOf("-file") != -1) {
			uri = otherArgs[argList.indexOf("-file") + 1];
			if (uri.toLowerCase().matches(".*png.*|.*jpg.*|.*gif.*")) {
				detectFaceinPngJpgEtc(uri);
			} else if (uri.toLowerCase().matches(".*ppm.*")) {
				detectFaceInPPM(uri);
			} else {
				System.err.println("unsupported image format");
				System.exit(2);
			}
		} else if (argList.indexOf("-dir") != -1) {
			uri = otherArgs[argList.indexOf("-dir") + 1];
			if (argList.indexOf("-ext") != -1) {
				String ext = otherArgs[argList.indexOf("-ext") + 1];
				detectFacesInDir(uri, ext);
			} else {
				System.err.println(usage);
				System.exit(2);
			}
		} else {
			System.err.println(usage);
			System.exit(2);
		}
	}

	public static void detectFacesInDir(String dir, String ext) throws Exception {
		List<String> fileURIs = Util.listFiles(dir, ext);
		for (String uri : fileURIs) {
			if (uri.toLowerCase().matches(".*png.*|.*jpg.*|.*gif.*")) {
				detectFaceinPngJpgEtc(uri);
			} else if (uri.toLowerCase().matches(".*ppm.*")) {
				detectFaceInPPM(uri);
			} else {
				logger.error("unsupported image format:" + ext);
				System.exit(1);
			}
		}
	}

	/**
	 * Detect face from image files in sequencefile format stored on a HDFS
	 * directory
	 * 
	 * @param hdfsdir
	 *            e.g., hdfs://master:8020/
	 * @param ext
	 *            e.g., seq, standard for image file in sequencefile format.
	 * @throws IOException
	 **/
	public static void detectFace(String hdfsdir, String ext) throws Exception {
		if (hdfsdir.contains("hdfs://")) {
			conf.set("fs.defaultFS", hdfsdir);
			FileStatus[] status = FileSystem.get(conf).listStatus(new Path(hdfsdir));
			for (int i = 0; i < status.length; i++) {
				Path path = status[i].getPath();
				if (path.getName().endsWith(ext) && status[i].getLen() > 0 && !status[i].isSymlink()) {
					logger.debug(status[i].getPath() + " " + status[i].getLen());
					detectFaceinPngJpgEtc(path.toString());
				}
			}
		} else if (hdfsdir.contains("s3://")) {
			List<String> sequris = Util.listFiles(hdfsdir, "seq");
			for (String uri : sequris) {
				detectFaceinPngJpgEtc(uri);
			}
		}
	}

	/**
	 * Detect face from image in PNG, JPG, BMP etc. where supported by Jave
	 * ImageIO out-of-box
	 * 
	 * @param uri
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	public static int detectFaceinPngJpgEtc(String uri) {
		Map<Text, byte[]> imagesequnce = null;
		if (uri.startsWith("s3n://"))
			try {
				imagesequnce = Util.readSequenceFileFromS3(uri);
			} catch (IOException e) {
				logger.warn("Cannot read SequnceFile from S3: " + uri);
				return -1;
			}
		else if (uri.startsWith("hdfs://"))
			try {
				imagesequnce = Util.readSequenceFileFromHDFS(uri);
			} catch (IOException e) {
				logger.warn("Cannot read SequnceFile from HDFS:" + uri);
				return -1;
			}
		else {
			logger.error("only hdfs:// and s3n;// sequncefile accesses are implemented");
			System.exit(1);
		}

		for (Map.Entry<Text, byte[]> entry : imagesequnce.entrySet()) {
			String filename = entry.getKey().toString();

			try {
				BufferedImage rawimage = ImageIO.read(new ByteArrayInputStream(entry.getValue()));
				int faces = detectFace(rawimage);
				logger.info("hostname=" + hostname + " filename=" + filename + " faces=" + faces);
				return faces; // 0: no face; 1: one face, ...
			} catch (IOException e) {
				logger.warn("Cannot read bytes into image");
				return -1;
			}
		}
		return -1; // indicate error in image sequencefile
	}

	/**
	 * Detect face from image in PPM format
	 * 
	 * @param uri
	 * @return
	 */
	public static int detectFaceInPPM(String uri) {
		Map<Text, byte[]> imagesequnce = null;
		if (uri.startsWith("s3n://"))
			try {
				imagesequnce = Util.readSequenceFileFromS3(uri);
			} catch (IOException e) {
				logger.warn("Cannot read SequnceFile from S3: " + uri);
				return -1;
			}

		else if (uri.startsWith("hdfs://"))
			try {
				imagesequnce = Util.readSequenceFileFromHDFS(uri);
			} catch (IOException e) {
				logger.warn("Cannot read SequnceFile from HDFS:" + uri);
				return -1;
			}
		else {
			logger.error("only hdfs:// and s3n;// sequncefile accesses are implemented");
			System.exit(1);
		}
		for (Map.Entry<Text, byte[]> entry : imagesequnce.entrySet()) {
			String filename = entry.getKey().toString();
			try {
				ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(entry.getValue()));
				BufferedImage rawimage = PPMImageReader.read(iis);
				int faces = detectFace(rawimage);
				logger.info("hostname= " + hostname + " filename= " + filename + " faces= " + faces);
				return faces;
			} catch (IOException e) {
				logger.warn("Cannot read bytes into image");
				return -1;
			}
		}
		return -1; // indicate error in image sequencefile
	}

	public static int detectFace(BufferedImage rawimage) {
		String faceClassifierPath = new File(OpenCV.class.getResource("/haarcascade_frontalface_alt.xml").getFile())
				.getAbsolutePath();
		int faces = detect(rawimage, faceClassifierPath);
		logger.debug("faces=" + faces);
		return faces;
	}

	public static int detectEye(BufferedImage rawimage) {
		String eyeClassifierPath = new File(OpenCV.class.getResource("/haarcascade_eye.xml").getFile())
				.getAbsolutePath();
		int eyes = detect(rawimage, eyeClassifierPath);
		logger.debug("eyes=" + eyes);
		return eyes;
	}

	private static int detect(BufferedImage rawimage, String classifierPath) {
		CvHaarClassifierCascade classifier = new CvHaarClassifierCascade(cvLoad(classifierPath));
		IplImage origImg = IplImage.createFrom(rawimage);
		CvMemStorage storage = CvMemStorage.create();
		CvSeq target = cvHaarDetectObjects(origImg, classifier, storage, 1.1, 3, CV_HAAR_DO_CANNY_PRUNING);
		cvClearMemStorage(storage);

		for (int i = 0; i < target.total(); i++) {
			CvRect r = new CvRect(cvGetSeqElem(target, i));
			if (logger.isDebugEnabled())
				logger.debug("x=" + r.x() + " y=" + r.y() + " x+w=" + r.x() + r.width() + " y+h=" + r.y() + r.height());
		}
		if (logger.isDebugEnabled())
			logger.debug("image width=" + rawimage.getWidth() + " height=" + rawimage.getHeight()
					+ " num of identified object=" + target.total());
		return target.total();
	}

}
