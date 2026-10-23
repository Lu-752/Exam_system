package com.exam.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuNode {
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String icon;
    private String perm;
    private String type;
    private Integer sortNo;
    private List<MenuNode> children = new ArrayList<>();
}
