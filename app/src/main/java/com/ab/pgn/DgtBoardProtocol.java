package com.ab.pgn;

/**
 * Sourse: https://github.com/nurv/lirec/blob/master/scenarios/MyFriend/GameCompanion/iCatChess/iCatChess/ElectronicBoard/dgtbrd13.h
 * converted to Java, omitting irrelevant information, preserving the original language
 *
 * Created by Alexander Bootman on 3/18/18.
 */
public class DgtBoardProtocol {
/*
Protocol description for DGT chess board.
Copyright 1998 DGT Projects B.V

Version: 1.03 Single computer and bus support in one .h file

*********************************************************
This protocol is protected under trade mark registration and copyrights.
It may not be used commercially without written permission
of DGT Projects B.V. It is illegal to transfer any registered trade mark
identifications by means of this protocol between any chessboard or other
application and any computer.
*********************************************************

Main functionality of the DGT Electronic Chess Board
----------------------------------------------------

The DGT board is basically a sensor which senses the presense of the special
chess set pieces on the squares of the board. The situation on the board is
measured and can be communicated with an average maximum time delay of
200 mS.
Besides this detection function, the board communicates with an optional
DGT TopMatch Chess Clock, to give the data of the clock available to the
general interface.
Finally the board is equipped with an internal storage of the measured
piece positions.

The board supports two methods of communication: for single-board situations
a protocol for communication between one board and one computer is available.

For situations with many boards a network communications protocol is
available, where many boards can be connected in a bus structure. A separate
communication protocol is available for this bus structure.

The communication protocol for single board connections is described
in the following paragraph "Single board communication protocol". This
paragraph describes much more than only the communication protocol. All
developers should read this paragraph, even if they would only use bus
communication.

The special bus communication protocol is derived from the single board
communication and functionality, where the main added feature is the
possibility to address a specific board on a shared communication bus.
The commands and data contens are described in the paragraph "Bus
Communication Protocol", Note however that the contens can not be understood
without reading the single board communication paragraph.


Paragraph: Single board communication protocol
----------------------------------------------

The main function of the board is to transfer piece position information.
For this, three modes are available:
1. IDLE mode. This cancelles any of the two UPDATE modes. No automatic
transfer of moves.
2. UPDATE_BOARD mode. On the moment that the board detects a removal, change
or placing of a piece, it outputs a DGT_SEND_UPDATE message
3. UPDATE mode. As UPDATE_BOARD mode, where additional the clock data are send
regularly (at least every second)

The board accepts command codes from the computer RS232. The commands are
1-byte codes, sometimes followed by data (see code definition)
The board can send data to the computer. Data always carries a message header.
The message header contains a message code and the total message size in bytes.
The start of the incoming message can be recognised by the MSB of the message
identifier set to 1 (see definition).

Board to computer communication interfaces:
RS232 for communication with computer, receiving commands, sending data
- 9600 Baud, 1 stopbit, 1 startbit, no parity
- No use of handshaking, neither software nor hardware

Connection between Digital Game Timer TopMatch Clock and the board:
Based on NEC SBI protocol. Adaption of the definition given in
the DGT TopMatch documentation.

Connector assignments for DGT Electronic Board: See User
and Programmers Manual

Related to the before mentioned modes, and to piece position information
transfer, the following commands to the board are available:
1. DGT_SEND_RESET
puts the DGT Board in IDLE mode
2. DGT_SEND_CLK
on which the DGT board responds with a DGT_MSG_BWTIME message containing clock
information
3. DGT_SEND_BRD
on which the DGT Board responds with a DGT_MSG_BOARD_DUMP message containing
the actual piece exising of all fields
4. DGT_SEND_UPDATE puts the DGT Board in the UPDATE mode, FRITZ5 compatible
5. DGT_SEND_UPDATE_BRD puts the DGT Board in the UPDATE_BOARD mode
6. DGT_SEND_UPDATE_NICE puts the board in UPDATE mode, however transferring
   only clocktimes when any time info changed.

The DGT_SEND_CLK command and the DGT_SEND_BOARD command do not affect the current board
mode: i.e. when in UPDATE mode, it continues sending DGT_SEND_UPDATE messages.

Board Identification:
Each DGT Electronic Board carries a unique serial number,
a EEPROM configuration version number and a embedded program version number.
These data are unalterable by the users.
Current identification is:
"DGT Projects - This DGT board is produced by DGT Projects.\n
DGT Projects is a registered trade mark.\n
220798 ISP/bus/8KP/8KE/P6/Fritz5 Vs 1.00. Serial nr. 00137 1.0"

The board can be loaded by the user with a non-volatile one-byte bus number,
for future use with multiple board configurations.

On-board EEPROM:
The board carries a 8 kB cyclic non-volatile memory, in which all position
changes and clock information is stored during all power-up time. This
file can be read and processed.


Start of Definitions:
---------------------*/

    public static final byte
        DGT_SEND_RESET = 0x40,		/* puts the board in IDLE mode, cancelling any UPDATE mode */
        DGT_SEND_CLK = 0x41,		/* results in a DGT_MSG_BWTIME message   */
        DGT_SEND_BRD = 0x42,		/* results in a DGT_MSG_BOARD_DUMP message   */
        DGT_SEND_UPDATE = 0x43,		/* results in DGT_MSG_FIELD_UPDATE messages and DGT_MSG_BWTIME messages
                                       as long as the board is in UPDATE mode  */
        DGT_SEND_UPDATE_BRD = 0x44,	/* results in DGT_MSG_FIELD_UPDATE messages
                                       as long as the board is in UPDATE_BOARD mode  */
        DGT_RETURN_SERIALNR = 0x45,	/* results in a DGT_MSG_SERIALNR message   */
        DGT_RETURN_BUSADRES = 0x46,	/* results in a DGT_MSG_BUSADRES message   */
        DGT_SEND_TRADEMARK = 0x47,	/* results in a DGT_MSG_TRADEMARK message   */
        DGT_SEND_EE_MOVES = 0x49,	/* results in a DGT_MSG_EE_MOVES message   */
        DGT_SEND_UPDATE_NICE = 0x4b,/* results in DGT_MSG_FIELD_UPDATE messages and DGT_MSG_BWTIME messages,
                                       the latter only at time changes, as long as the board is in UPDATE_NICE mode*/
        DGT_SEND_VERSION = 0x4d,	/* results in a DGT_MSG_VERSION message   */


/* DESCRIPTION OF THE MESSAGES FROM BOARD TO PC

A message consists of three header bytes:
MESSAGE ID             one byte, MSB (MESSAGE BIT) always 1
MSB of MESSAGE SIZE    one byte, MSB always 0, carrying D13 to D7 of the
					   total message length, including the 3 header byte
LSB of MESSAGE SIZE    one byte, MSB always 0, carrying  D6 to D0 of the
					   total message length, including the 3 header bytes
followed by the data:
0 to ((2 EXP 14) minus 3) data bytes, of which the MSB is always zero.
*/

/* DEFINITION OF THE BOARD-TO-PC MESSAGE ID CODES and message descriptions */

        /* ID codes: */
        DGT_NONE = 0x00,
        DGT_BOARD_DUMP = 0x06,
        DGT_BWTIME = 0x0d,
        DGT_FIELD_UPDATE = 0x0e,
        DGT_EE_MOVES = 0x0f,
        DGT_BUSADRES = 0x10,
        DGT_SERIALNR = 0x11,
        DGT_TRADEMARK = 0x12,
        DGT_VERSION = 0x13,

        /* DGT_BOARD_DUMP is the message that follows on a DGT_SEND_BOARD command */
        DGT_SIZE_BOARD_DUMP = 67,

        /* message format:
        byte 0: DGT_MSG_BOARD_DUMP
        byte 1: LLH_SEVEN(DGT_SIZE_BOARD_DUMP) (=0 fixed)
        byte 2: LLL_SEVEN(DGT_SIZE_BOARD_DUMP) (=67 fixed)
        byte 3-66: Pieces on position 0-63

        Board fields are numbered from 0 to 63, row by row, in normal reading
        sequence. When the connector is on the left hand, counting starts at
        the top left square. The board itself does not rotate the numbering,
        when black instead of white plays with the clock/connector on the left hand.
        In non-rotated board use, the field numbering is as follows:

        Field A8 is numbered 0
        Field B8 is numbered 1
        Field C8 is numbered 2
        ..
        Field A7 is numbered 8
        ..
        Field H1 is numbered 63

        So the board always numbers the black edge field closest to the connector
        as 57.

        Piece codes for chess pieces: */
        EMPTY = 0x00,
        WPAWN = 0x01,
        WROOK = 0x02,
        WKNIGHT = 0x03,
        WBISHOP = 0x04,
        WKING = 0x05,
        WQUEEN = 0x06,
        BPAWN = 0x07,
        BROOK = 0x08,
        BKNIGHT = 0x09,
        BBISHOP = 0x0a,
        BKING = 0x0b,
        BQUEEN = 0x0c,

/* message format DGT_FIELD_UPDATE: */

        DGT_SIZE_FIELD_UPDATE = 5,

/*
byte 0: DGT_MSG_FIELD_UPDATE
byte 1: LLH_SEVEN(DGT_SIZE_FIELD_UPDATE) (=0 fixed)
byte 2: LLL_SEVEN(DGT_SIZE_FIELD_UPDATE) (=5 fixed)
byte 3: field number (0-63) which changed the piece code
byte 4: piece code including EMPTY, where a non-empty field became empty
*/


/* message format: DGT_TRADEMARK which returns a trade mark message */

/*
byte 0: DGT_MSG_TRADEMARK
byte 1: LLH_SEVEN(DGT_SIZE_TRADEMARK) 
byte 2: LLL_SEVEN(DGT_SIZE_TRADEMARK) 
byte 3-end: ASCII TRADEMARK MESSAGE, codes 0 to 0x3F 
The value of DGT_SIZE_TRADEMARK is not known beforehand, and may be in the 
range of 0 to 256
Current trade mark message: ...
*/
        dummy_byte = 0;

    //  MESSAGE_BIT is set in each message byte[0]
    public static final int MESSAGE_BIT = 0x080;
}
