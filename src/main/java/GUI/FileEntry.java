/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 *
 * @author francescop
 */
public final class FileEntry{
    private final StringProperty matches = new SimpleStringProperty("");
    private final StringProperty fileName = new SimpleStringProperty("");
    private final StringProperty owner = new SimpleStringProperty("");

    public FileEntry(int matches, String name, String owner) {
        setMatches(matches);
        setFileName(name);
        setOwner(owner);
    }

    public int getMatches() {
        return Integer.parseInt(matches.get());
    }
    public void setMatches(int matches) {
        this.matches.set(""+matches);
    }

    public String getName() {
        return fileName.get();
    }
    public void setFileName(String fileName) {
        this.fileName.set(fileName);
    }

    public String getOwner() {
        return owner.get();
    }
    public void setOwner(String owner) {
        this.owner.set(owner);
    }
    
    @Override
    public String toString(){
        return getName()+": "+getMatches()+" matches; owner: "+getOwner();
    }
}
