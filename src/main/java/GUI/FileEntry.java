/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package GUI;

/**
 *
 * @author francescop
 */
public class FileEntry {
    private int matches;
    private String name;
    private String owner;

    public FileEntry(int matches, String name, String owner) {
        this.matches = matches;
        this.name = name;
        this.owner = owner;
    }

    public int getMatches() {
        return matches;
    }

    public void setMatches(int matches) {
        this.matches = matches;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    @Override
    public String toString(){
        return name+": "+matches+"matches; owner: "+owner;
    }
}
