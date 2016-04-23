/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

import java.math.BigInteger;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 *
 * @author francescop
 */
public final class FileEntry{
    private final IntegerProperty matches = new SimpleIntegerProperty();
    private final StringProperty fileName = new SimpleStringProperty("");
    private final ObjectProperty<BigInteger> owner = new SimpleObjectProperty<>();

    public FileEntry(int matches, String name, BigInteger owner) {
        setMatches(matches);
        setFileName(name);
        setOwner(owner);
    }

    public int getMatches() {
        return matches.get();
    }
    public void setMatches(int matches) {
        this.matches.set(matches);
    }

    public String getFileName() {
        return fileName.get();
    }
    public void setFileName(String fileName) {
        this.fileName.set(fileName);
    }

    public BigInteger getOwner() {
        return owner.get();
    }
    public void setOwner(BigInteger owner) {
        this.owner.set(owner);
    }
    
    @Override
    public String toString(){
        return getFileName()+": "+getMatches()+" matches; owner: "+getOwner();
    }
}
