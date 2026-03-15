package com.sky.mapper;


import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealDishMapper {
    /**
     * 根据菜品id查询套餐id
     * @param dishIds
     * @return
     */

    List<Long> getSetmealIdsByDishIds(List<Long> dishIds);

    /**
     * 修改套餐
     * @param setmeal
     */
    void update(Setmeal setmeal);

    void insertBatch(List<SetmealDish> setmealDishes);

    /**
     * 删除套餐和菜品的关联关系
     * @param id
     */
    @Delete("delete from setmeal_dish where setmeal_id=#{id}")
    void deleteBySetmealId(Long id);

    /**
     * 批量删除
     * @param ids
     */
    void deleteSetmealDishAll(List<Long> ids);

    @Select("select * from setmeal_dish where setmeal_id = #{setmealId}")
    List<SetmealDish> getBySetmealId(Long setmealId);
}
