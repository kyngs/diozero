/*
 * #%L
 * Organisation: diozero
 * Project:      Device I/O Zero - Native System Utilities
 * Filename:     com_diozero_internal_provider_sysfs_NativeSerialDevice.c
 *
 * This file is part of the diozero project. More information about this project
 * can be found at http://www.diozero.com/
 * %%
 * Copyright (C) 2016 - 2020 diozero
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <unistd.h>

#include <errno.h>
#include <string.h>

#include <fcntl.h>
#include <sys/file.h>
#include <sys/ioctl.h>

#include <termios.h>
#include <linux/serial.h>

#include "com_diozero_internal_provider_sysfs_NativeSerialDevice.h"

#define SERIAL_READ_NO_DATA -1
#define SERIAL_READ_FAILED -2

#define DATA_BITS_5_ORDINAL 0
#define DATA_BITS_6_ORDINAL 1
#define DATA_BITS_7_ORDINAL 2
#define DATA_BITS_8_ORDINAL 3

#define NO_PARITY_ORDINAL 0
#define EVEN_PARITY_ORDINAL 1
#define ODD_PARITY_ORDINAL 2
#define MARK_PARITY_ORDINAL 3
#define SPACE_PARITY_ORDINAL 4

#define ONE_STOP_BIT_ORDINAL 0
#define TWO_STOP_BITS_ORDINAL 1

tcflag_t getDataBitsFlag(int dataBits) {
	tcflag_t data_bits_flag;
	switch (dataBits) {
	case DATA_BITS_5_ORDINAL:
		data_bits_flag = CS5;
		break;
	case DATA_BITS_6_ORDINAL:
		data_bits_flag = CS6;
		break;
	case DATA_BITS_7_ORDINAL:
		data_bits_flag = CS7;
		break;
	case DATA_BITS_8_ORDINAL:
	default:
		data_bits_flag = CS8;
	}
	return data_bits_flag;
}

tcflag_t getParityFlag(int parity) {
	tcflag_t parity_flag;
	switch (parity) {
	case EVEN_PARITY_ORDINAL:
		parity_flag = PARENB;
		break;
	case ODD_PARITY_ORDINAL:
		parity_flag = (PARENB | PARODD);
		break;
	case MARK_PARITY_ORDINAL:
		parity_flag = (PARENB | PARODD | CMSPAR);
		break;
	case SPACE_PARITY_ORDINAL:
		parity_flag = (PARENB | CMSPAR);
		break;
	case NO_PARITY_ORDINAL:
	default:
		parity_flag = 0;
		break;
	}
	return parity_flag;
}

speed_t getBaudVal(int baud) {
	speed_t speed;
	switch (baud) {
	case     50: speed =     B50; break;
	case     75: speed =     B75; break;
	case    110: speed =    B110; break;
	case    134: speed =    B134; break;
	case    150: speed =    B150; break;
	case    200: speed =    B200; break;
	case    300: speed =    B300; break;
	case    600: speed =    B600; break;
	case   1200: speed =   B1200; break;
	case   1800: speed =   B1800; break;
	case   2400: speed =   B2400; break;
	case   4800: speed =   B4800; break;
	case   9600: speed =   B9600; break;
	case  19200: speed =  B19200; break;
	case  38400: speed =  B38400; break;
	case  57600: speed =  B57600; break;
	case 115200: speed = B115200; break;
	case 230400: speed = B230400; break;

	default:
		speed = B9600;
	}

	return speed;
}

/*
 * Class:     com_diozero_internal_provider_sysfs_NativeSerialDevice
 * Method:    serialOpen
 * Signature: (Ljava/lang/String;IIII)I
 */
JNIEXPORT jint JNICALL Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialOpen
  (JNIEnv* env, jclass clz, jstring device, jint baud, jint dataBits, jint stopBits, jint parity) {
	const char* filename = (*env)->GetStringUTFChars(env, device, NULL);
	int fd = open(filename, O_RDWR | O_NOCTTY | O_NONBLOCK);
	(*env)->ReleaseStringUTFChars(env, device, filename);

	if (fd < 0) {
		return fd;
	}

	// Clear any serial port flags and set up raw, non-canonical port parameters
	fcntl(fd, F_SETFL, 0);
	struct termios options = {0};
	tcgetattr(fd, &options);
	cfmakeraw(&options);

	options.c_cc[VMIN]  = 0;
	//options.c_cc[VTIME] = readTimeout / 100
	options.c_cc[VTIME] = 0;

	tcflush(fd, TCIFLUSH);
	tcsetattr(fd, TCSANOW, &options);

	// Configure the port parameters
	Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialConfigPort(
			env, clz, fd, baud, dataBits, stopBits, parity);

	return fd;
}

/*
 * Class:     com_diozero_internal_provider_sysfs_NativeSerialDevice
 * Method:    serialConfigPort
 * Signature: (IIIII)I
 */
JNIEXPORT jint JNICALL Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialConfigPort
  (JNIEnv* env, jclass clz, jint fd, jint baud, jint dataBits, jint parity, jint stopBits) {
	struct termios options = {0};
	tcgetattr(fd, &options);

	options.c_cflag &= ~(CSIZE | PARENB | CMSPAR | PARODD);
	options.c_cflag |= (getDataBitsFlag(dataBits) | getParityFlag(parity) | CLOCAL | CREAD);

	if (stopBits == ONE_STOP_BIT_ORDINAL) {
		options.c_cflag &= ~CSTOPB;
	} else {
		options.c_cflag |= CSTOPB;
	}

	speed_t speed = getBaudVal(baud);
	cfsetispeed(&options, speed);
	cfsetospeed(&options, speed);

	return tcsetattr(fd, TCSANOW, &options);
}

/*
 * Class:     com_diozero_internal_provider_sysfs_NativeSerialDevice
 * Method:    serialReadByte
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialReadByte
  (JNIEnv* env, jclass clz, jint fd) {
	char x;
	int rc = read(fd, &x, 1);

	if (rc == 1) {
		return ((int) x) & 0xFF;
	}

	if ((rc == -1) && (errno == EAGAIN)) {
		// No data
		return SERIAL_READ_NO_DATA;
	}

	// Read failed
	return SERIAL_READ_FAILED;
}

/*
 * Class:     com_diozero_internal_provider_sysfs_NativeSerialDevice
 * Method:    serialWriteByte
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialWriteByte
  (JNIEnv* env, jclass clz, jint fd, jint bVal) {
	unsigned char c = (unsigned char) bVal;
	return write(fd, &c, 1);
}

/*
 * Class:     com_diozero_internal_provider_sysfs_NativeSerialDevice
 * Method:    serialRead
 * Signature: (I[B)I
 */
JNIEXPORT jint JNICALL Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialRead
  (JNIEnv* env, jclass clz, jint fd, jbyteArray rxData) {
	jbyte* rx_buf = (*env)->GetByteArrayElements(env, rxData, NULL);
	int rx_length = (*env)->GetArrayLength(env, rxData);

	int rc = read(fd, (uint8_t*) rx_buf, rx_length);

	(*env)->ReleaseByteArrayElements(env, rxData, rx_buf, 0);

	return rc;
}

/*
 * Class:     com_diozero_internal_provider_sysfs_NativeSerialDevice
 * Method:    serialWrite
 * Signature: (I[B)I
 */
JNIEXPORT jint JNICALL Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialWrite
  (JNIEnv* env, jclass clz, jint fd, jbyteArray txData) {
	jbyte* tx_buf = (*env)->GetByteArrayElements(env, txData, NULL);
	int tx_length = (*env)->GetArrayLength(env, txData);

	int rc = write(fd, (uint8_t*) tx_buf, tx_length);

	(*env)->ReleaseByteArrayElements(env, txData, tx_buf, 0);

	return rc;
}

/*
 * Class:     com_diozero_internal_provider_sysfs_NativeSerialDevice
 * Method:    serialBytesAvailable
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialBytesAvailable
  (JNIEnv* env, jclass clz, jint fd) {
	int result;
	int rc = ioctl(fd, FIONREAD, &result);
	if (rc < 0) {
		return rc;
	}
	return result;
}

/*
 * Class:     com_diozero_internal_provider_sysfs_NativeSerialDevice
 * Method:    serialClose
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_diozero_internal_provider_sysfs_NativeSerialDevice_serialClose
  (JNIEnv* env, jclass clz, jint fd) {
	return close(fd);
}