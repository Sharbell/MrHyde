package org.faudroids.mrhyde.jekyll;

import android.content.Context;

import com.google.common.base.Optional;

import org.faudroids.mrhyde.R;
import org.faudroids.mrhyde.git.FileUtils;
import org.faudroids.mrhyde.git.GitManager;
import org.faudroids.mrhyde.utils.ObservableUtils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rx.Observable;
import timber.log.Timber;

/**
 * Handles Jekyll specific tasks for one repository.
 */
public class JekyllManager {

  private static final String
      DIR_NAME_POSTS = "_posts",
      DIR_NAME_DRAFTS = "_drafts";

  private static final Pattern
      POST_TITLE_PATTERN = Pattern.compile("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d)(.+)\\..+"),
      DRAFT_TITLE_PATTERN = Pattern.compile("(.+)\\..+");

  private static final DateFormat POST_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

  private final Context context;
  private final FileUtils fileUtils;
  private final File dirPosts, dirDrafts;

  JekyllManager(Context context, FileUtils fileUtils, GitManager gitManager) {
    this.context = context;
    this.fileUtils = fileUtils;
    this.dirPosts = new File(gitManager.getRepository().getRootDir(), DIR_NAME_POSTS);
    this.dirDrafts = new File(gitManager.getRepository().getRootDir(), DIR_NAME_DRAFTS);
  }


  /**
   * Returns all posts sorted by date with the newest first.
   */
  public Observable<List<Post>> getAllPosts() {
    if (!dirPosts.exists()) return Observable.just(new ArrayList<>());

    return fileUtils
        .getAllFilesInDirectory(dirPosts)
        .map(postFiles -> {
          // parse titles
          List<Post> posts = new ArrayList<>();
          for (File postFile : postFiles) {
            Optional<Post> post = parsePost(postFile);
            if (post.isPresent()) posts.add(post.get());
          }

          // sort by date
          Collections.sort(posts);
          Collections.reverse(posts);
          return posts;
        });
  }


  /**
   * Returns all drafts sorted by title.
   */
  public Observable<List<Draft>> getAllDrafts() {
    if (!dirDrafts.exists()) return Observable.just(new ArrayList<>());

    return fileUtils
        .getAllFilesInDirectory(dirDrafts)
        .map(draftFiles -> {
          // parse titles
          List<Draft> drafts = new ArrayList<>();
          for (File draftFile : draftFiles) {
            Optional<Draft> draft = parseDraft(draftFile);
            if (draft.isPresent()) drafts.add(draft.get());
          }

          // sort by title
          Collections.sort(drafts);
          return drafts;
        });
  }


  /**
   * Converts a jekyll post title to the corresponding jekyll filename.
   */
  public String postTitleToFilename(String title) {
    if (!title.isEmpty()) title = "-" + title;
    return POST_DATE_FORMAT.format(Calendar.getInstance().getTime()) + draftTitleToFilename(title);
  }


  /**
   * Converts a jekyll draft title to the corresponding filename.
   */
  public String draftTitleToFilename(String title) {
    return title.replaceAll(" ", "-") + ".md";
  }


  /**
   * Creates and returns a new post file.
   */
  public Observable<Post> createNewPost(final String title) {
    return createNewPost(title, dirPosts);
  }


  public Observable<Post> createNewPost(final String title, final File postDir) {
    return ObservableUtils
        .fromSynchronousCall((ObservableUtils.Func<Void>) () -> {
          if (!postDir.exists()) if (!postDir.mkdir()) Timber.w("Failed to create posts dir");
          return null;
        })
        .flatMap(aVoid -> {
          File targetFile = new File(postDir, postTitleToFilename(title));
          return fileUtils
              .createNewFile(targetFile)
              .flatMap(nothing -> setupDefaultFrontMatter(targetFile, title))
              .map(nothing -> new Post(title, Calendar.getInstance().getTime(), targetFile));
        });
  }


  /**
   * Creates and returns a new draft file.
   */
  public Observable<Draft> createNewDraft(final String title) {
    return createNewDraft(title, dirDrafts);
  }


  public Observable<Draft> createNewDraft(final String title, File draftDir) {
    return ObservableUtils
        .fromSynchronousCall((ObservableUtils.Func<Void>) () -> {
          if (!draftDir.exists()) if (!draftDir.mkdir()) Timber.w("Failed to create drafts dir");
          return null;
        })
        .flatMap(aVoid -> {
          File targetFile = new File(draftDir, draftTitleToFilename(title));
          return fileUtils
              .createNewFile(targetFile)
              .flatMap(nothing -> setupDefaultFrontMatter(targetFile, title))
              .map(nothing -> new Draft(title, targetFile));
        });
  }


  /**
   * Publishes a previously created draft to the _posts folder.
   *
   * @return the newly created post.
   */
  public Observable<Post> publishDraft(final Draft draft) {
    final String postTitle = postTitleToFilename(draft.getTitle());
    final Post post = new Post(postTitle, new Date(), new File(dirPosts, postTitle));
    return fileUtils
        .renameFile(draft.getFile(), post.getFile())
        .map(nothing -> post);
  }


  /**
   * Moves a previously created post to the _drafts folder.
   *
   * @return the newly created draft.
   */
  public Observable<Draft> unpublishPost(final Post post) {
    final String draftTitle = draftTitleToFilename(post.getTitle());
    final Draft draft = new Draft(draftTitle, new File(dirDrafts, draftTitle));
    return fileUtils
        .renameFile(post.getFile(), draft.getFile())
        .map(nothing -> draft);
  }


  /**
   * Returns true if the passed in dir is the Jekyll posts dir or sub dir.
   */
  public boolean isPostsDirOrSubDir(File directory) {
    return isSpecialDirOrSubDir(directory, DIR_NAME_POSTS);
  }


  /**
   * Returns true if the passed in dir is the Jekyll drafts dir or sub dir.
   */
  public boolean isDraftsDirOrSubDir(File directory) {
    return isSpecialDirOrSubDir(directory, DIR_NAME_DRAFTS);
  }


  private boolean isSpecialDirOrSubDir(File directory, String dirName) {
    File iter = directory;
    while (iter != null) {
      if (iter.getName().equals(dirName)) return true;
      iter = iter.getParentFile();
    }
    return false;
  }


  private Observable<Void> setupDefaultFrontMatter(File file, String title) {
    return fileUtils.writeFile(file, context.getString(R.string.default_front_matter, title));
  }


  /**
   * Tries reading one particular file as a post.
   */
  public Optional<Post> parsePost(File file) {
    String fileName = file.getName();

    // check for match
    Matcher matcher = POST_TITLE_PATTERN.matcher(fileName);
    if (!matcher.matches()) return Optional.absent();

    try {
      // get date
      int year = Integer.valueOf(matcher.group(1));
      int month = Integer.valueOf(matcher.group(2)) - 1; // java Calendar is 0 based
      int day = Integer.valueOf(matcher.group(3));
      Calendar calendar = Calendar.getInstance();
      calendar.set(year, month, day);

      // get title
      String title = formatTitle(matcher.group(4));

      return Optional.of(new Post(title, calendar.getTime(), file));

    } catch (NumberFormatException e) {
      Timber.w(e, "failed to parse post tile \"" + fileName + "\"");
      return Optional.absent();
    }
  }


  /**
   * Tries reading one particular file as a draft.
   */
  public Optional<Draft> parseDraft(File file) {
    // check for match
    Matcher matcher = DRAFT_TITLE_PATTERN.matcher(file.getName());
    if (!matcher.matches()) return Optional.absent();

    // get title
    String title = formatTitle(matcher.group(1));

    return Optional.of(new Draft(title, file));
  }


  /**
   * Removes dashes (-) from a title, replaces those with spaces ( ) and capitalizes
   * the first character.
   */
  private String formatTitle(String title) {
    title = title.replaceAll("-", " ");
    title = title.trim();
    title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
    return title;
  }

}
