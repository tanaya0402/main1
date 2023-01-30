package com.example.eye.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;//impuiifg

import com.example.eye.R;
import com.example.eye.adapter.UserAdapter;
import com.example.eye.listener.UsersListener;
import com.example.eye.utilities.Constants;
import com.example.eye.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.example.eye.modules.User;
//import com.google.firebase.firestore.auth.User;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.view.View.INVISIBLE;

public class MainActivity extends AppCompatActivity implements UsersListener {
        private PreferenceManager preferenceManager;
        private List<User> users;
        private UserAdapter userAdapter;
       private TextView textErrorMessage;
       private SwipeRefreshLayout swipeRefreshLayout;
        private ImageView imageConference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(getApplicationContext());

        imageConference = findViewById(R.id.imageConference);

        TextView textTitle = findViewById(R.id.textTitle);
        textTitle.setText(String.format(
                "%s %s",
                preferenceManager.getString(Constants.KEY_FIRST_NAME),
                preferenceManager.getString(Constants.KEY_LAST_NAME)

        ));

        findViewById(R.id.textSignOut).setOnClickListener(view -> signOut());


        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if(task.isSuccessful() && task.getResult() != null){
                sendFCMTokenToDatabase(task.getResult());
            }
        });
        /*FirebaseInstallations.getInstance().getToken(true).addOnCompleteListener(task -> {
            if(task.isSuccessful() && task.getResult() != null){
                sendFCMTokenToDatabase(task.getResult().getToken());


            }
        });*/

        RecyclerView userRecyclerView = findViewById(R.id.userRecyclerView);
        textErrorMessage = findViewById(R.id.textErrorMessage);
        //usersProgressBar = findViewById(R.id.userProgressBar);
         users = new ArrayList<>();
        userAdapter = new UserAdapter(users, this);
        userRecyclerView.setAdapter(userAdapter);

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::getUsers);
        getUsers();

    }

    private void getUsers(){
        swipeRefreshLayout.setRefreshing(true);
        textErrorMessage.setVisibility(INVISIBLE);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(task -> {
                    swipeRefreshLayout.setRefreshing(false);
                    String myUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    if(task.isSuccessful() && task.getResult() != null){
                        users.clear();
                        for (QueryDocumentSnapshot documentSnapshot : task.getResult()){
                            if(myUserId.equals(documentSnapshot.getId()) || (documentSnapshot.getString(Constants.KEY_FCM_TOKEN) == null)){
                                continue;
                            }
                            User user = new User();
                            user.firstName = documentSnapshot.getString(Constants.KEY_FIRST_NAME);
                            user.lastName = documentSnapshot.getString(Constants.KEY_LAST_NAME);
                            user.email = documentSnapshot.getString(Constants.KEY_EMAIL);
                            user.token= documentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                            users.add(user);
                        }
                        if(users.size()> 0){
                            userAdapter.notifyDataSetChanged();
                        }else {
                            users.clear();
                            userAdapter.notifyDataSetChanged();
                            textErrorMessage.setText(String.format("%s","No client available"));
                            textErrorMessage.setVisibility(View.VISIBLE);
                        }
                    }else {
                        textErrorMessage.setText(String.format("%s","No client available"));
                        textErrorMessage.setVisibility(View.VISIBLE);
                    }
                });
    }
    private void sendFCMTokenToDatabase(String token){
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = 
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Unable to send Token", Toast.LENGTH_SHORT).show());
    }

        //Sign out function
        private void signOut(){
            Toast.makeText(this, "Signing Out.......", Toast.LENGTH_SHORT).show();
            FirebaseFirestore database = FirebaseFirestore.getInstance();
            DocumentReference documentReference =
                    database.collection(Constants.KEY_COLLECTION_USERS).document(
                            preferenceManager.getString(Constants.KEY_USER_ID)
                    );
            HashMap<String, Object> updates = new HashMap<>();
            updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
            documentReference.update(updates)
                    .addOnSuccessListener(aVoid -> {
                        preferenceManager.clearPreference();
                        startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                    })
                    .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Unable to sign out!!!!", Toast.LENGTH_SHORT).show());
        }

    @Override
    public void initiateVideo(User user) {                  //for toast when video icon is clicked
        if(user.token == null || user.token.trim().isEmpty()){
            Toast.makeText(
                    this,
                    user.firstName + " " + user.lastName + " is not available",
                    Toast.LENGTH_SHORT
            ).show();
        }else {
            Intent intent =  new Intent(getApplicationContext(), OutgoingRequestActivity.class);
            intent.putExtra("user", user);
            intent.putExtra("type", "video");
            startActivity(intent);

        }
    }

    @Override
    public void onMultipleUsersAction(Boolean isMultipleUsersSelected) {
        if(isMultipleUsersSelected){
            imageConference.setVisibility(View.VISIBLE);
            imageConference.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent =  new Intent(getApplicationContext(), OutgoingRequestActivity.class);
                    intent.putExtra("selectedUsers", new Gson().toJson(userAdapter.getSelectedUsers()));
                    intent.putExtra("type","video");
                    intent.putExtra("isMultiple",true);
                    startActivity(intent);
                }
            });
        }
        else{
            imageConference.setVisibility(View.GONE);
        }
    }
}
