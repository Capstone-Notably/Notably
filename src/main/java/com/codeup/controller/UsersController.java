package com.codeup.controller;

import com.codeup.models.*;
import com.codeup.repositories.*;
import com.codeup.svcs.TwilioSvc;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by roxana on 6/29/17.
 */
@Controller
public class UsersController {
    private final UsersRepository usersRepository;
    private final RolesRepository rolesRepository;
    private final PasswordEncoder passwordEncoder;
    private final ItemsRepository itemsRepository;
    private final UserItemsRepository userItemsRepository;
    private final PreferenceRepository preferenceRepository;
    private final CategoriesRepository categoriesRepository;
    private final UserCategoryRepository userCategoryRepository;
    private final RecipesRepository recipesRepository;
    private final UserRecipeRepository userRecipeRepository;
    private final GroceryListsRepository groceryListsRepository;
    private final UserGListRepository userGListRepository;
    private final TwilioSvc twilioSvc;


    @Value("${users-img-path}")
    private String usersImgPath;

    @Autowired
    public UsersController(UsersRepository usersRepository, RolesRepository rolesRepository, PasswordEncoder passwordEncoder,
                           ItemsRepository itemsRepository, UserItemsRepository userItemsRepository, PreferenceRepository preferenceRepository,
                           CategoriesRepository categoriesRepository, UserCategoryRepository userCategoryRepository,
                           RecipesRepository recipesRepository, UserRecipeRepository userRecipeRepository,
                           GroceryListsRepository groceryListsRepository, UserGListRepository userGListRepository, TwilioSvc twilioSvc) {
        this.usersRepository = usersRepository;
        this.rolesRepository = rolesRepository;
        this.passwordEncoder = passwordEncoder;
        this.itemsRepository = itemsRepository;
        this.userItemsRepository = userItemsRepository;
        this.preferenceRepository = preferenceRepository;
        this.categoriesRepository = categoriesRepository;
        this.userCategoryRepository = userCategoryRepository;
        this.recipesRepository = recipesRepository;
        this.userRecipeRepository = userRecipeRepository;
        this.groceryListsRepository = groceryListsRepository;
        this.userGListRepository = userGListRepository;
        this.twilioSvc = twilioSvc;
    }

    @PostMapping("/users/register")
    public String saveUser(@Valid User user, Errors validation, @RequestParam(name = "preference") String preference,
                           @RequestParam(name = "file") MultipartFile uploadedFile, Model model) {

        if (validation.hasErrors()) {
            model.addAttribute("errors", validation);
            model.addAttribute("user", user);
            return "users/register";
        }

        String filename = transferUploadedFile(uploadedFile, usersImgPath, model);
        if(filename.isEmpty()) {
            filename = "default_user.png";
        }
        user.setImgUrl(filename);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        usersRepository.save(user);

        // create a default role for each user ROLE_USER
        UserRole userRole = new UserRole(user.getId(), "ROLE_USER");
        rolesRepository.save(userRole);

        //update table users_categories
        Preference prefByName = preferenceRepository.findByName(preference);
        List<Category> categories = categoriesRepository.findByUser_Id(1);
        for (Category category : categories) {
            String[] split = category.getPreferences().split(" ");
            for(int i=0; i<split.length; i++) {
                if (Integer.parseInt(split[i]) == prefByName.getId()) {
                    userCategoryRepository.save(new UserCategory(category, user, prefByName));
                }
            }
        }

        //update table users_items
        List<Item> items = itemsRepository.findByUser_Id(1);

        for (Item item : items) {
            String[] split = item.getPreferences().split(" ");
            for(int i=0; i<split.length; i++) {
                if (Integer.parseInt(split[i]) == prefByName.getId()) {
                    userItemsRepository.save(new UserItem(user, item, 1, 0.00));
                }
            }
        }

        //update table users_recipes
        //create temporal recipe to save items when user create a new recipe
        Recipe temporalRecipe = recipesRepository.save(new Recipe("temporal recipe", "recipe_default.png", "", user));
        List<Recipe> recipes = recipesRepository.findByUser_Id(1);
        for (Recipe recipe : recipes) {
            if(recipe.getPreference().getId() == prefByName.getId()) {
                userRecipeRepository.save(new UserRecipe(recipe, user));
            }
        }

        //update table users_glists
        GroceryList savedGlist = groceryListsRepository.save(new GroceryList("My grocery list"));
        userGListRepository.save(new UserGList(savedGlist, user));

//        // send a welcome text
//        String message = "Hello " + user.getUsername() + "from Get It";
//        twilioSvc.sendMessage("+12104219757",message);

        return "redirect:/login";
    }

    @GetMapping("/about")
    public String about(Model model) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!principal.equals("anonymousUser")) {
            User user = (User) principal;
            model.addAttribute("user", user);
        }
        return "about";
    }

    @GetMapping("/users/profile")
    public String profile(Model model) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        model.addAttribute("user", user);
        return "users/profile";
    }

    @PostMapping("/users/profile")
    public String updateProfile(@ModelAttribute User user, @RequestParam(name = "file") MultipartFile uploadedFile, Model model) {
        User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String filename = transferUploadedFile(uploadedFile, usersImgPath, model);
        if(filename.isEmpty()) {
            user.setImgUrl(principal.getImgUrl());
        } else {
            user.setImgUrl(filename);
        }
        user.setId(principal.getId());
        user.setPassword(principal.getPassword());
        usersRepository.save(user);
        model.addAttribute("user", user);
        return "users/profile";
    }


    public static String transferUploadedFile(MultipartFile uploadedFile, String imgPath, Model model) {
        String filename = uploadedFile.getOriginalFilename();
        String filepath = Paths.get(imgPath, filename).toString();
        File destinationFile = new File(filepath);

        try {
            uploadedFile.transferTo(destinationFile);
        } catch (IOException e) {
            e.printStackTrace();
            model.addAttribute("message", "Oops! Something went wrong! " + e);
        }
        return filename;
    }
}
