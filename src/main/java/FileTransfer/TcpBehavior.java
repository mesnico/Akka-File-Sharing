/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Alessandro
 */
public enum TcpBehavior {
    SEND_FILE, SEND_FILENAME, SEND_FILE_NOW,
    REQUEST_FILE, SEND_NAME_OF_REQUESTED_FILE, AUTHORIZATION_REPLY_HANDLE, RECEIVE_FILE_NOW,
    UNINITIALIZED;
}
