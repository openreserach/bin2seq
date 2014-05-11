mvn clean compile package -DskipTests=true
export LIBJARS="$HOME/.m2/repository/com/amazonaws/aws-java-sdk/1.0.002/aws-java-sdk-1.0.002.jar,$HOME/.m2/repository/org/hdfgroup/hdf-java/2.6.1/hdf-java-2.6.1.jar,$HOME/.m2/repository/edu/ucar/netcdf/4.2.20/netcdf-4.2.20.jar,$JAVACV_BIN_HOME//javacv.jar,$JAVACV_BIN_HOME/javacpp.jar,$JAVACV_BIN_HOME/javacv-linux-x86_64.jar,$JAVACV_CPP_HOME/opencv-2.4.8-linux-x86_64.jar,$JAVACV_CPP_HOME/ffmpeg-2.1.1-linux-x86_64.jar"
export HADOOP_CLASSPATH=`echo ${LIBJARS} | sed s/,/:/g`
export CLASSPATH=`echo ${LIBJARS} | sed s/,/:/g`
#hadoop jar ./target/bin2seq-0.1.jar com.openresearchinc.hadoop.sequencefile.OpenCV -file s3://ori-tmp/lena.png.seq -libjars $LIBJARS -Djava.library.path=/home/heq/opencv/release/lib/
#hadoop jar ./target/bin2seq-0.1.jar com.openresearchinc.hadoop.sequencefile.OpenCV -file s3://ori-tmp/lena.png.seq -libjars $LIBJARS 
#hadoop jar ./target/bin2seq-0.1.jar com.openresearchinc.hadoop.sequencefile.OpenCV -file hdfs://master:8020/tmp/lena.png.seq -libjars $LIBJARS
#hadoop jar ./target/bin2seq-0.1.jar com.openresearchinc.hadoop.sequencefile.OpenCV -file hdfs://master:8020/tmp/00001_930831_fa_a.ppm.seq -libjars $LIBJARS
hadoop jar ./target/bin2seq-0.1.jar com.openresearchinc.hadoop.sequencefile.OpenCV -dir s3n://ori-colorferet-seq/ -ext ppm.seq -libjars $LIBJARS
hadoop jar ./target/bin2seq-0.1.jar com.openresearchinc.hadoop.sequencefile.OpenCV -dir hdfs:///colorferet-seq/ -ext ppm.seq -libjars $LIBJARS



