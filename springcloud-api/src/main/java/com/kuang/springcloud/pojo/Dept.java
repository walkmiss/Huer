package com.kuang.springcloud.pojo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author: jiabin
 * @create: 2019/12/111:04
 * @description:
 **/
@Data
@Accessors(chain = true) //链式写法
public class Dept implements Serializable {

    private Long deptno;

    private String deptname;

    private String db_source;


    public Dept(String deptname) {
        this.deptname = deptname;
    }
}
