/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package FileTransfer.messages;

/**
 *
 * @author Alessandro
 */
public enum EnumEnding {
    CONNECTION_FAILED, 
    FILE_TO_SEND_BUSY, 
    FILE_TO_SEND_NOT_EXISTS, 
    FILE_OPENING_FAILED, 
    NOT_ENOUGH_SPACE_FOR_SENDING,
    IO_ERROR_WHILE_SENDING,
    FILE_NO_MORE_BUSY,
    FILE_SENDING_FAILED, 
    FILE_SENT_SUCCESSFULLY,
    FILE_TO_RECEIVE_BUSY, 
    FILE_TO_RECEIVE_NOT_EXISTS, 
    NOT_ENOUGH_SPACE_FOR_RECEIVING,
    IO_ERROR_WHILE_RECEIVING, 
    FILE_RECEIVING_FAILED, 
    FILE_RECEIVED_SUCCESSFULLY,
}
