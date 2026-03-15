package com.sky.mapper;


import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    List<ShoppingCart> list(ShoppingCart shoppingCart);

    @Update("update shopping_cart set number=#{number} where id=#{id}")
    void updateNumberById(ShoppingCart shoppingCart);

    /*
    * 插入购物车
    *
    * */
    @Insert("insert into shopping_cart (name,user_id,dish_id,setmeal_id,dish_flavor,number,amount,image,create_time) " +
            "VALUES (#{name},#{userId},#{dishId},#{setmealId},#{dishFlavor},#{number},#{amount},#{image},#{createTime})")
    void insert(ShoppingCart shoppingCart);

    /*
    * 清除购物车
    * */
    @Delete("delete from shopping_cart where user_id=#{currentid}")
    void deleteById(Long currentid);



    /*
    * 查找购物车
    *
    * */
/*    @Select("select * from shopping_cart where setmeal_id=#{setmealId} and dish_id=#{dishId} and dish_flavor=#{dishFlavor}")
    ShoppingCart shearch(ShoppingCartDTO shoppingCartDTO);*/


    /*
    * 删除一件购物车商品
    * */
    void deleteByDishId(ShoppingCart shoppingCart);

    /*
    * 另一种方案，直接提取购物车商品id进行删除，更高效
    *
    * */
    @Delete("delete from shopping_cart where id = #{id}")
    void deleteByShoppingCartId(Long id);

    void insertBatch(List<ShoppingCart> shoppingCartList);
}
