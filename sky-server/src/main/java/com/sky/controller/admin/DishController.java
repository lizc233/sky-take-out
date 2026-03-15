package com.sky.controller.admin;


import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("admin/dish")
@Slf4j
@Api(tags="菜品相关接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;
    /*
    * 新增菜品
    * @param dishDTO
    * @return
    * */
    @PostMapping
    @ApiOperation(value="新增菜品")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("新增菜品:{}",dishDTO);
        dishService.savewithFlavor(dishDTO);
        //清理缓存数据
        String key="dish_"+dishDTO.getCategoryId();
        cleanCache(key );
        return Result.success();
    }
    /*
    * 菜品查询
    * @param page
    * @param pageSize
    * @param name
    * @return
    * */
    @GetMapping("/page")
    @ApiOperation(value="菜品查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("菜品查询:{}",dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);

    }
    /*
    * 删除菜品
    * @param id
    * @return
    * */
    @ApiOperation(value="删除菜品")
    @DeleteMapping
    public Result delete(@RequestParam List<Long> ids){
        log.info("删除菜品:{}",ids);
        dishService.deleteBatch(ids);
        //将所有菜品缓存数据清除，所有以dish_开头的key
        cleanCache("dish_*");
        return Result.success();
    }

    /*
    * 根据id查询菜品
    * @param id
    * @return
    * */
    @ApiOperation(value="根据id查询菜品")
    @GetMapping("/{id}")
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("查询菜品:{}",id);
        DishVO dishVO= dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    /*
    * 编辑菜品
    * @param dishDTO
    * @return
    * */
    @PutMapping
    @ApiOperation(value="编辑菜品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("编辑菜品:{}",dishDTO);
        dishService.updateWithFlavor(dishDTO);
        //将所有菜品缓存数据清除，所有以dish_开头的key
        cleanCache("dish_*");
        return Result.success(dishDTO);
    }

    /*
    * 设置菜品状态
    * @param status
    * @param id
    * @return
    * */
    @ApiOperation(value="设置菜品状态")
    @PostMapping("/status/{status}")
    public Result setStatus(@PathVariable Integer status,@RequestParam Long id){
        log.info("设置菜品状态:{}",status);
        dishService.startOrStop(status,id);
        //将所有菜品缓存数据清除，所有以dish_开头的key
        cleanCache("dish_*");
        return Result.success(status);
    }


    /*
    * 根据分类id查询菜品
    * @param categoryId
    * @return
    * */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE); // 重点：通常只查询起售中的菜品

        // 注意：这里调用的应该是 dishService 的方法
        List<DishVO> list = dishService.listWithCategoryId(dish);
        return Result.success(list);
    }

    /*
    * 清理缓存数据
    * */
    private void cleanCache(String pattern){
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }

}
