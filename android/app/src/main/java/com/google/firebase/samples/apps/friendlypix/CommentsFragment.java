package com.google.firebase.samples.apps.friendlypix;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ServerValue;
import com.firebase.client.ValueEventListener;
import com.firebase.ui.FirebaseRecyclerAdapter;
import com.google.firebase.samples.apps.friendlypix.Models.Comment;
import com.google.firebase.samples.apps.friendlypix.Models.Person;


/**
 * This fragment shows the comments for a post.
 * Use the {@link CommentsFragment#newInstance} factory method to
 * create an instance of this fragment and pass in the Firebase
 * post key ref as the argument.
 */
public class CommentsFragment extends Fragment {
    public static final String TAG = "CommentsFragment";
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String POST_REF_PARAM = "post_ref_param";
    private static final int DEFAULT_MSG_LENGTH_LIMIT = 256;
    private EditText mEditText;
    private RecyclerView.Adapter<CommentViewHolder> mAdapter;

    private String mPostRef;

    public CommentsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param postRef The firebase post ref.
     * @return A new instance of fragment CommentsFragment.
     */
    public static CommentsFragment newInstance(String postRef) {
        CommentsFragment fragment = new CommentsFragment();
        Bundle args = new Bundle();
        args.putString(POST_REF_PARAM, postRef);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mPostRef = getArguments().getString(POST_REF_PARAM);
        } else {
            throw new RuntimeException("You must specify a post reference.");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_comments, container, false);
        RecyclerView mCommentsView = (RecyclerView) rootView.findViewById(R.id.comment_list);
        mEditText = (EditText) rootView.findViewById(R.id.editText);
        final Button sendButton = (Button) rootView.findViewById(R.id.send_comment);

        final Firebase commentsRef = FirebaseUtil.getBaseRef().child("comments").child(mPostRef);
        mAdapter = new FirebaseRecyclerAdapter<Comment, CommentViewHolder>(
                Comment.class, R.layout.comment_item, CommentViewHolder.class, commentsRef) {
            @Override
            protected void populateViewHolder(final CommentViewHolder viewHolder,
                                              Comment comment, int position) {
                String authorId = comment.getAuthor(); // TODO: fix this to be comment.getUserId()
                FirebaseUtil.getBaseRef().child("people").child(authorId)
                        .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                Person author = dataSnapshot.getValue(Person.class);
                                viewHolder.commentAuthor.setText(author.getDisplayName());
                                GlideUtil.loadProfileIcon(author.getPhotoUrl(), viewHolder
                                        .commentPhoto);
                            }

                            @Override
                            public void onCancelled(FirebaseError firebaseError) {
                                new RuntimeException("Couldn't get comment username.",
                                        firebaseError.toException());
                            }
                        }
                );
                viewHolder.authorRef = authorId;
                viewHolder.commentTime
                        .setText(DateUtils.getRelativeTimeSpanString((long) comment.getTimestamp
                                ()));
                viewHolder.commentText.setText(comment.getText());
            }
        };
        sendButton.setEnabled(false);
        mEditText.setHint(R.string.new_comment_hint);
        mEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter
                (DEFAULT_MSG_LENGTH_LIMIT)});
        mEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0) {
                    sendButton.setEnabled(true);
                } else {
                    sendButton.setEnabled(false);
                }
            }
        });
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Clear input box and hide keyboard.
                final Editable commentText = mEditText.getText();
                mEditText.setText("");
                InputMethodManager inputManager =
                        (InputMethodManager) getActivity().
                                getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(
                        mEditText.getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);

                Comment comment = new Comment(FirebaseUtil.getCurrentUserId(),
                        commentText.toString(), ServerValue.TIMESTAMP);
                commentsRef.push().setValue(comment, new Firebase.CompletionListener() {
                    @Override
                    public void onComplete(FirebaseError error, Firebase firebase) {
                        if (error != null) {
                            Log.w(TAG, "Error posting comment: " + error.getMessage());
                            Toast.makeText(getActivity(), "Error posting comment.", Toast
                                    .LENGTH_SHORT).show();
                            mEditText.setText(commentText);
                        }
                    }
                });
            }
        });
        mCommentsView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mCommentsView.setAdapter(mAdapter);
        return rootView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAdapter != null && mAdapter instanceof FirebaseRecyclerAdapter) {
            ((FirebaseRecyclerAdapter) mAdapter).cleanup();
        }
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        public final ImageView commentPhoto;
        public final TextView commentText;
        public final TextView commentAuthor;
        public final TextView commentTime;
        public String authorRef;

        public CommentViewHolder(View itemView) {
            super(itemView);
            commentPhoto = (ImageView) itemView.findViewById(R.id.comment_author_icon);
            commentText = (TextView) itemView.findViewById(R.id.comment_text);
            commentAuthor = (TextView) itemView.findViewById(R.id.comment_name);
            commentTime = (TextView) itemView.findViewById(R.id.comment_time);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (authorRef != null) {
                        Context context = v.getContext();
                        Intent userDetailIntent = new Intent(context, UserDetailActivity.class);
                        userDetailIntent.putExtra(UserDetailActivity.USER_ID_EXTRA_NAME,
                                authorRef);
                        context.startActivity(userDetailIntent);
                    }
                }
            });
        }
    }
}