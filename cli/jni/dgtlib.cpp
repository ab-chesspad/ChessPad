//#define _DEBUG 1

/*
https://www.ebay.com/itm/TECHTOO-USB-2-0-to-RS232-DB9-Serial-MALE-A-Converter-Adapter-Serial-Cable-3ft-OS/202306963130
https://www.amazon.com/TECHTOO-Adapter-Converter-Chipset-Andorid/dp/B01JWD8LKG

How to build:

export JAVA_HOME=/usr/lib/jvm/java-8-oracle (or something like this)
navigate to jni directory

javac -h . -d ../target  ../com/ab/pgn/dgtboard/DgtBoardInterface.java ../../app/src/main/java/com/ab/pgn/dgtboard/DgtBoardIO.java ../../app/src/main/java/com/ab/pgn/dgtboard/DgtBoardProtocol.java
or
javac -h . -d ../target  ../com/ab/pgn/dgtboard/DgtBoardInterface.java ../../src/main/java/com/ab/pgn/dgtboard/DgtBoardIO.java ../../src/main/java/com/ab/pgn/dgtboard/DgtBoardProtocol.java

edit dgtlib.cpp if needed

Linux:
g++ -fPIC -shared -I$JAVA_HOME/include -I$JAVA_HOME/include/linux -I. -o lib/linux/dgtlib.so dgtlib.cpp [-D_DEBUG=1]

Mac OSX:
gcc -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" -dynamiclib -o lib/macosx/dgtlib.dylib dgtlib.cpp [-D_DEBUG=1]

Windows:
Install java, Visual Studio Community Edition
Open dgtlib.vcxproj
Select platform, build

*/

#if _DEBUG == 1
#define DEBUG_PRINT(x) printf x
//#define DEBUG_PRINT(x)
#else
#define DEBUG_PRINT(x)
#endif

#if _WIN32 == 1 || _WIN64 == 1
#include "windows.h"
#include <stdio.h>
#include <stdlib.h>
#include <jni.h>
#include "dgtbrd13.h"
#include "com_ab_pgn_dgtboard_DgtBoardInterface.h"

using namespace std;
typedef HANDLE JNI_HANDLE;

HANDLE read_thread;
int skip_read = 0;
OVERLAPPED overlapped;
HANDLE oev;
unsigned char charBuf = '\0';
BOOL keepReading = TRUE;
int readCount = 0;

#else
#include <sys/ioctl.h>
#include <unistd.h>
#include <termios.h>
#include <sys/time.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <termio.h>
#include<jni.h>
#include "dgtbrd13.h"
#include "com_ab_pgn_dgtboard_DgtBoardInterface.h"

typedef long JNI_HANDLE;

#endif

JNI_HANDLE tty = (JNI_HANDLE)-1;

JNI_HANDLE _open_tty(const char* tty_name, int timeout);
void _close_tty(JNI_HANDLE tty);
void _dgt_write(JNI_HANDLE tty, const unsigned char command);
int _dgt_read(JNI_HANDLE tty, signed char* buffer, int offset, int buf_len, int timeout_msec);

int get_timeout(JNIEnv *env, jobject thisObj) {
    // Get a reference to this object's class
    jclass thisClass = env->GetObjectClass(thisObj);

    // Get the Field ID of the instance variables "tty"
    jfieldID fid_timeout = env->GetFieldID(thisClass, "timeout_msec", "I");
    if (NULL == fid_timeout) {
        return 0;
    }
    // Get the int given the Field ID
    jint timeout = env->GetIntField(thisObj, fid_timeout);
//    DEBUG_PRINT(("In CPP, timeout=%d\n", timeout));
    return timeout;
}

JNIEXPORT void JNICALL Java_com_ab_pgn_dgtboard_DgtBoardInterface__1open
        (JNIEnv *env, jobject thisObj, jstring _tty_name) {
    DEBUG_PRINT(("ab_pgn_dgtboard_DgtBoardInterface__1open\n"));
    const char* tty_name = env->GetStringUTFChars(_tty_name, 0);
    DEBUG_PRINT(("ab_pgn_dgtboard_DgtBoardInterface__1open %s\n", tty_name));

    // Get a reference to this object's class
    jclass thisClass = env->GetObjectClass(thisObj);

    int timeout = get_timeout(env, thisObj);
    tty = _open_tty(tty_name, timeout);
    DEBUG_PRINT(("open tty_name=%s, tty=%08lx, timeout=%d\n", tty_name, (long)tty, timeout));
}

JNIEXPORT void JNICALL Java_com_ab_pgn_dgtboard_DgtBoardInterface__1close
(JNIEnv *env, jobject thisObj) {
    DEBUG_PRINT(("close tty=%08lx\n", (long)tty));
    if (tty >= 0) {
        _close_tty(tty);
    }
}

JNIEXPORT void JNICALL Java_com_ab_pgn_dgtboard_DgtBoardInterface__1write
(JNIEnv *env, jobject thisObj, jbyte command) {
    DEBUG_PRINT(("write tty=%09lx, command=0x%02x\n", (long)tty, command));
    if (tty >= 0) {
        _dgt_write(tty, (const unsigned char)command);
    }
}

JNIEXPORT jint JNICALL Java_com_ab_pgn_dgtboard_DgtBoardInterface__1read
(JNIEnv *env, jobject thisObj, jbyteArray buffer, jint offset, jint length, jint _timeout_msec) {
    int timeout = get_timeout(env, thisObj);

    //  jsize buf_len = env->GetArrayLength(buffer);
    DEBUG_PRINT(("read tty=%08lx, offset=%d, length=%d, timeout=%d\n", (long)tty, offset, length, timeout));
    jbyte* body = env->GetByteArrayElements(buffer, 0);
    jint res = _dgt_read(tty, body, offset, length, timeout);
    env->ReleaseByteArrayElements(buffer, body, 0);
    return res;
}

#if _WIN32 == 1 || _WIN64 == 1
JNI_HANDLE _open_tty(const char* tty_name, int timeout) {
	JNI_HANDLE tty = CreateFile(tty_name, GENERIC_READ | GENERIC_WRITE, 0, NULL, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, NULL);
	DEBUG_PRINT(("serial handled created\n"));
	// Do some basic settings
	DCB dcb = { 0 };
	dcb.DCBlength = sizeof(dcb);
	GetCommState(tty, &dcb);

	dcb.BaudRate = CBR_9600;
	dcb.fBinary = TRUE;     /* Binary Mode (skip EOF check)    */
	dcb.fParity = NOPARITY;     /* Enable parity checking          */
	dcb.fOutxCtsFlow = FALSE; /* CTS handshaking on output       */
	dcb.fOutxDsrFlow = FALSE; /* DSR handshaking on output       */
	dcb.fDtrControl = DTR_CONTROL_ENABLE;  /* DTR Flow control                */
	dcb.fDsrSensitivity = FALSE; /* DSR Sensitivity              */
	dcb.fTXContinueOnXoff = FALSE; /* Continue TX when Xoff sent */
	dcb.fOutX = TRUE;       /* Enable output X-ON/X-OFF        */
	dcb.fInX = TRUE;        /* Enable input X-ON/X-OFF         */
	dcb.fErrorChar = FALSE;  /* Enable Err Replacement          */
	dcb.fNull = FALSE;       /* Enable Null stripping           */
	dcb.fRtsControl = RTS_CONTROL_ENABLE;  /* Rts Flow control                */
	dcb.fAbortOnError = FALSE; /* Abort all reads and writes on Error */
	dcb.ByteSize = 8;        /* Number of bits/byte, 4-8        */
	dcb.Parity = 0;          /* 0-4=None,Odd,Even,Mark,Space    */
	dcb.StopBits = ONESTOPBIT;        /* 0,1,2 = 1, 1.5, 2               */
	if (!SetCommState(tty, &dcb)) {
		printf("ERROR in SetCommState\n");
	}
	// Set timeouts
	COMMTIMEOUTS timeouts = { 0 };
	timeouts.ReadIntervalTimeout = 1;
	timeouts.ReadTotalTimeoutConstant = 0;
	timeouts.ReadTotalTimeoutMultiplier = 0;
	timeouts.WriteTotalTimeoutConstant = 0;
	timeouts.WriteTotalTimeoutMultiplier = 0;
	if (!SetCommTimeouts(tty, &timeouts)) {
		printf("ERROR in SetCommTimeouts\n");
	}
	if (!SetCommMask(tty, 0x1fff)) {
		printf("ERROR in SetCommMask\n");
	}
	oev = CreateEvent(NULL, TRUE, FALSE, NULL);
	return tty;
}

void _close_tty(JNI_HANDLE tty) {
//	CancelSynchronousIo(read_thread);
	keepReading = FALSE;
	Sleep(100);
	CloseHandle(tty);
	CloseHandle(oev);
}

void _dgt_write(JNI_HANDLE tty, const unsigned char command) {
	DWORD bytesWritten;
	OVERLAPPED overlapped = { 0 };
	HANDLE oev = CreateEvent(NULL, TRUE, FALSE, NULL);
	signed char ch[1];
	ch[0] = command;

	memset(&overlapped, 0, sizeof(OVERLAPPED));
	overlapped.hEvent = oev;
	BOOL status = WriteFile(tty, ch, 1, &bytesWritten, &overlapped);
	if (!status) {
		DWORD err = GetLastError();
		DEBUG_PRINT(("WriteFile err=%04x\n", err));
		if (err == ERROR_IO_PENDING) {
			status = GetOverlappedResult(tty, &overlapped, &bytesWritten, TRUE);
			DEBUG_PRINT(("write, GetOverlappedResult %d bytes, status=%d\n", bytesWritten, status));
			if (!status) {
				err = GetLastError();
				DEBUG_PRINT(("write, GetOverlappedResult err=%04x\n", err));
			}
		}
	}
	if (status) {
		DEBUG_PRINT(("'%c' written, res=%d\n", ch[0], status));
	}
	CloseHandle(oev);
}

int _dgt_read(JNI_HANDLE tty, signed char* buffer, int offset, int length, int timeout_msec) {
	DWORD bytesRead = 0;
	int byteCount = 0;
	while (keepReading) {
		Sleep(10);
//		DEBUG_PRINT(("_dgt_read loop %d\n", ++readCount));
		DWORD err = ERROR_IO_PENDING;
		int status = 0;
		if (!skip_read) {
			memset(&overlapped, 0, sizeof(OVERLAPPED));
			overlapped.hEvent = oev;
			status = ReadFile(tty, &charBuf, 1, &bytesRead, &overlapped);
			*(buffer + offset) = charBuf;
			if (status) {
				DEBUG_PRINT(("ReadFile %02x\n", charBuf));
			} else {
				err = GetLastError();
				DEBUG_PRINT(("ReadFile err=%04x, %02x\n", err, charBuf));
				if (err == ERROR_IO_PENDING) {
					if (byteCount > 0) {
						skip_read = 1;
						return byteCount;
					}
				}
			}
		}
		skip_read = 0;

		if (!status && err == ERROR_IO_PENDING) {
			DEBUG_PRINT(("hang on WaitForSingleObject %d\n", ++readCount));
			status = WaitForSingleObject(overlapped.hEvent, INFINITE);
			if (status) {
				DEBUG_PRINT(("WaitForSingleObject returns %04x, %02x\n", status, charBuf));
			}
			else {
				status = GetOverlappedResult(tty, &overlapped, &bytesRead, FALSE);
				*(buffer + offset) = charBuf;
				if (status) {
					DEBUG_PRINT(("GetOverlappedResult %d bytes, %02x\n", bytesRead, charBuf));
				}
				else {
					err = GetLastError();
					printf("GetOverlappedResult err=%04x, %02x\n", err, charBuf);
				}
			}
		}
		byteCount += bytesRead;
		offset += bytesRead;
	}
	return byteCount;
}

#else

JNI_HANDLE _open_tty(const char* tty_name, int timeout) {
    struct termios trm;
    int set, retval, tty;

    DEBUG_PRINT(("open %s\n", tty_name));
    tty = open(tty_name, O_RDWR | O_NOCTTY);
//    tty = open(tty_name, O_RDWR | O_NDELAY);
    if (tty < 0) {
        DEBUG_PRINT(("error unable to open %s\n", tty_name));
        return -1;
    }
    ioctl(tty, TIOCMGET, &set);
    set |= TIOCM_DTR;     /* DTR high */
    ioctl(tty, TIOCMSET, &set);
    tcflush(tty, TCIOFLUSH);      /* flush buffers */
    retval = tcgetattr(tty, &trm);
    cfsetispeed(&trm, B9600);     /* input speed 9600 */
    cfsetospeed(&trm, B9600);     /* output speed 9600 */
    cfmakeraw(&trm);      /* raw input/output mode */
    retval = tcsetattr(tty, TCSANOW, &trm);
    if (retval < 0) {
    	DEBUG_PRINT(("error unable to set up I/O port\n"));
	return 0;
    }
    return tty;
}

void _close_tty(JNI_HANDLE tty) {
    close(tty);
}

void _dgt_write(JNI_HANDLE tty, const unsigned char command) {
    write(tty, &command, 1);
    sleep(3);
}

int _dgt_read(JNI_HANDLE tty, signed char* buffer, int offset, int length, int timeout_msec) {
    unsigned int bytes;

    fd_set read_fds;
    struct timeval timeout;

    timeout.tv_sec = 0;
    timeout.tv_usec = timeout_msec * 1000;
    FD_ZERO(&read_fds);
    FD_SET(tty, &read_fds);

    if (select(32, &read_fds, 0, 0, &timeout) != 1) {
        // timeout or error
        DEBUG_PRINT(("timeout, skip\n"));
        return 0;
    }
    bytes = 0;
    while (bytes == 0) {
    	bytes += read(tty, buffer + offset, length);
    }
    DEBUG_PRINT(("read %d bytes\n", bytes));
    return bytes;
}
#endif

