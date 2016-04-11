/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI.messages;

import java.io.Serializable;

/**
 *
 * @author nicky
 */
public class SearchRequest implements Serializable{
    private String searchString;

    public SearchRequest(String searchString) {
        this.searchString = searchString;
    }

    public String getSearchString() {
        return searchString;
    }    
}
