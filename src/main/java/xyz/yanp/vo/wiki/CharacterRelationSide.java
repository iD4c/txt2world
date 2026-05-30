package xyz.yanp.vo.wiki;

import lombok.Data;

@Data
public class CharacterRelationSide {
    private String name;
    private RelationDimensions relationDimensionsToOther;
}
