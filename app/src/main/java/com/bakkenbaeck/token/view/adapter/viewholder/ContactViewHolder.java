package com.bakkenbaeck.token.view.adapter.viewholder;


import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bakkenbaeck.token.R;
import com.bakkenbaeck.token.model.local.User;
import com.bakkenbaeck.token.view.custom.StarRatingView;

public class ContactViewHolder extends ClickableViewHolder {

    private ImageView avatar;
    private TextView name;
    private TextView username;
    private StarRatingView ratingView;

    public ContactViewHolder(final View view) {
        super(view);
        this.name = (TextView) view.findViewById(R.id.name);
        this.username = (TextView) view.findViewById(R.id.username);
        this.avatar = (ImageView) view.findViewById(R.id.avatar);
        this.ratingView = (StarRatingView) view.findViewById(R.id.rating_view);
    }

    public void setUser(final User user) {
        this.name.setText(user.getDisplayName());
        this.username.setText(user.getUsername());
        this.avatar.setImageBitmap(user.getImage());
        this.ratingView.setStars(3.6);
    }
}