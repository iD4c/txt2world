package xyz.yanp.vo.wiki;

import lombok.Data;

import java.util.List;

@Data
public class MergedWikiData {

    private List<WorldObj> characters;
    private List<WorldObj> factions;
    private List<WorldObj> items;
}
