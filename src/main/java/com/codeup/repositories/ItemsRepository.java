package com.codeup.repositories;

import com.codeup.models.Items;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Created by roxana on 6/27/17.
 */
@Repository
public interface ItemsRepository  extends CrudRepository<Items, Long>{
}
