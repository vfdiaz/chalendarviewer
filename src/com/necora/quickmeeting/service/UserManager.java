/**
    This file is part of QuickMeeting.

    QuickMeeting is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    QuickMeeting is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with QuickMeeting.  If not, see <http://www.gnu.org/licenses/>.    
*/

package com.necora.quickmeeting.service;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.necora.quickmeeting.contentprovider.AccountColumns;
import com.necora.quickmeeting.objects.User;
import com.necora.quickmeeting.util.ConnectionUtils;

import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Manager users
 *
 */
public class UserManager {

    /** Tag for logging */
     static private final String TAG = UserManager.class.toString();

    /** instance reference */
    private static UserManager sInstance = null;
        
    /** Active user id */
    private String mUserId;
    
    /** Active user Mail */
    private String mUserMail = "";
    
    /** Active user access Token */
    private String mAccessToken;
    
    /** Refresh Token value */
    private String mRefreshToken;
    
    /** ExpirationDate value */
    private Date   mExpirationDate;
    
    /** Data Formatter */
    private SimpleDateFormat mDateFormatter;

    /** QuickMeeting Provider object */
    private ContentResolver mProvider;    
    
    /**
     * Internal Constructor
     * @param context app context
     */
    private UserManager(Context context) {
        
        // use the sqlite format for date
        mDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        
        
        //TODO return exception if it is null
        mProvider = context.getContentResolver();
        
        recoverDataFromDataBase();
    }
    
    /**
     * Get mail of active user
     * @param accessToken google access token
     * @return mail of active user
     */
    private String getUserMail(String accessToken) {
        
        String userMail = null;
        
        String[] paramsKey =  {"Authorization"};
        String[] paramsValue = {"Bearer " + accessToken};
        
        String googleResponse = null;
        try {
            googleResponse = ConnectionUtils.getHttpsGetConnection(GoogleConstants.URL_USER_INFO, paramsKey, paramsValue);
        } catch (IllegalStateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (HttpException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        JSONObject jsonUserObj;
        try {
            jsonUserObj = (JSONObject) new JSONTokener(googleResponse).nextValue();
            userMail = (jsonUserObj.getString(User.FIELD_EMAIL));
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return userMail;
    }
    
    /**
     * Get data from database
     * @return true if data is recovered, false otherwise
     */
    private boolean recoverDataFromDataBase(){
        
        // return value
        boolean dataRecovered = false;
        
        // Form an array specifying which columns to return. 
        String[] projection = new String[] {
                AccountColumns._ID,
                AccountColumns.EMAIL,
                AccountColumns.ACCESS_TOKEN,
                AccountColumns.REFRESH_TOKEN,
                AccountColumns.EXPIRATION_DATE
        };
        String whereClause = AccountColumns.ACTIVE_USER + " = ?";
        String[] whereArgs = new String[]{"1"};
        
        // Make the query. 
        Cursor managedCursor = mProvider.query(
                AccountColumns.CONTENT_URI, // uri                
                projection,           // Which columns to return 
                whereClause,          // Which rows to return (active user)
                whereArgs,            // Selection arguments 
                null                  // Put the results in ascending order by email
        );       
        
        // is there at least one user?
        if (managedCursor.moveToFirst()) {
            
            int userIdColumn         = managedCursor.getColumnIndex(AccountColumns._ID);
            int userMailColumn       = managedCursor.getColumnIndex(AccountColumns.EMAIL);
            int accessTokenColumn    = managedCursor.getColumnIndex(AccountColumns.ACCESS_TOKEN);
            int refreshTokenColumn   = managedCursor.getColumnIndex(AccountColumns.REFRESH_TOKEN);
            int expirationDateColumn = managedCursor.getColumnIndex(AccountColumns.EXPIRATION_DATE);
                        
            try {
                mUserId         = managedCursor.getString(userIdColumn);
                mUserMail       = managedCursor.getString(userMailColumn);
                mAccessToken    = managedCursor.getString(accessTokenColumn);
                mRefreshToken   = managedCursor.getString(refreshTokenColumn);
                mExpirationDate = mDateFormatter.parse(managedCursor.getString(expirationDateColumn));
                dataRecovered = true;
                
                // for each cursor that you do not close, a little panda dies..
                managedCursor.close();
                
            } catch (ParseException e) {
                // failed to recover expiration date, get new token
                mExpirationDate = Calendar.getInstance().getTime();                
                dataRecovered = refreshToken();
            }
        } else {
            // no users
            dataRecovered = false;
        }        
        
        return dataRecovered;        
    }
    
    /**
     * Refresh access Token
     * @return true if refresh is ok, false otherwise
     */
    private boolean refreshToken() {
        
        // temporary Calendar
        Calendar tCalendar = Calendar.getInstance();
        
        // temporary json object
        JSONObject jsonTokenObj;
        
        // temporary access Token var
        String accessToken = null;
        
        // temporary expiration Date var
        Date expirationDate = null;
        
        // return value
        boolean refrehTokenReturn = true;
        

        //verify if token still valid
        if (tCalendar.getTime().before(mExpirationDate) == true) {
            //token is valid
            refrehTokenReturn = true;
        } else {
            
            // TODO create constants to these parameters
            String[] paramsKey =   {"client_id",/*"client_secret",*/"refresh_token","grant_type"};
            String[] paramsValue = {GoogleConstants.CLIENT_ID,/*GoogleConstants.CLIENT_SECRET,*/mRefreshToken,"refresh_token"};
            
            // connect to google and get the response
            String googleResponse = null;
            try {
                googleResponse = ConnectionUtils.doHttpsPostFormUrlEncoded(GoogleConstants.URL_ACCESS_TOKEN, paramsKey, paramsValue);
                
                /************ Parse the response ************/
                jsonTokenObj = (JSONObject) new JSONTokener(googleResponse).nextValue();
                accessToken = jsonTokenObj.getString("access_token");            
                
                // set new expiration time (current time + valid period)
                tCalendar.add(Calendar.SECOND, Integer.parseInt(jsonTokenObj.getString("expires_in")));                
                expirationDate = tCalendar.getTime();
                             
                /************ Content OK, update user */
                if (updateActiveUser(accessToken,expirationDate) == false) {
                    refrehTokenReturn = false;
                }
                
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                refrehTokenReturn = false;
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                refrehTokenReturn = false;
                e.printStackTrace();
            } catch (HttpException e) {
                // TODO Auto-generated catch block
                refrehTokenReturn = false;
                e.printStackTrace();
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                refrehTokenReturn = false;
                e.printStackTrace();
            }
        }
        
        return refrehTokenReturn;
    }

    /**
     * Update active user with new access token and expiration date to this token
     * @param accessToken - new accessToken
     * @param expirationDate - new Expiration date
     * @return if active user was updated
     */
    private boolean updateActiveUser(String accessToken, Date expirationDate) {

        // return Value
        boolean activeUserWasUpdated = false; 
        
        // data to change
        ContentValues values = new ContentValues();

        values.put(AccountColumns.ACCESS_TOKEN, accessToken);
        values.put(AccountColumns.EXPIRATION_DATE, mDateFormatter.format(expirationDate));
        
        if (updateUser(mUserMail, values) == true) {
            mAccessToken = accessToken;
            mExpirationDate = expirationDate;
            activeUserWasUpdated = true;
        }
        
        return activeUserWasUpdated;
    }

    /**
     * Update an user with new data
     * @param userMail - user's to update
     * @param values - values to update
     * @return if user was updated
     */
    private boolean updateUser(String userMail, ContentValues values) {
        
        // return Value
        boolean userWasUpdated = false; 

        // where clause
        String where = AccountColumns.EMAIL + " = ?";
        String[] whereParams = new String[]{userMail};
        
        // update the row
        int rowsAffected = mProvider.update(AccountColumns.CONTENT_URI, values, where, whereParams);
        
        //verify if user is updated
        if (rowsAffected == 1) {
            userWasUpdated = true;
        }
        
        return userWasUpdated;        
    }
    
    /**
     * Add a active user token
     * @param authorizationCode google authorization code
     * @return true: OK, otherwise false
     */
    public boolean addActiveUserToken(String authorizationCode) {
        Log.d(TAG, "addActiveUserToken begin");
        
        // return value
        boolean userWasAdded = false;
        
        // HTML response 
        String HTMLresponse = null;
        
        // Active user Access token
        String accessToken = null;
        
        // Active user Refresh token
        String refreshToken = null;
        
         // Active user's mail
        String userMail = null;
        
        // period in sec's to expirate current token
        Date expirationDate = null;
        
        // temp calendar
        Calendar tCalendar = Calendar.getInstance();
        
        //values to be inserted/updated
        ContentValues values = new ContentValues();        
        
        // recover user internal data
        try {
            // fill parameters
            // TODO create constants
            String[] paramsKey = {"client_id",/*"client_secret",*/"code","redirect_uri","grant_type"};        
            String[] paramsValue = {GoogleConstants.CLIENT_ID, /*GoogleConstants.CLIENT_SECRET,*/ authorizationCode, GoogleConstants.OAUTH_REDIRECT_URI,"authorization_code"}; 
            HTMLresponse = ConnectionUtils.doHttpsPostFormUrlEncoded(GoogleConstants.URL_ACCESS_TOKEN, paramsKey, paramsValue);
            
            JSONObject jsonObj = (JSONObject) new JSONTokener(HTMLresponse).nextValue();
            
            //TODO create constants
            //recover accessToken and refreshToken
            accessToken  = jsonObj.getString("access_token");
            refreshToken = jsonObj.getString("refresh_token");
            
            //set date 
            tCalendar.add(Calendar.SECOND, Integer.parseInt(jsonObj.getString("expires_in")));                
            expirationDate = tCalendar.getTime();

            //getUserMail
            userMail = getUserMail(accessToken);
            
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (HttpException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }        
        
        // mail is not empty or null
        if (userMail != null && !userMail.equals("")) {
        
            //this part needs to be synchronized to execute
            synchronized (mUserMail) {
                
                //verify if user exists on database
                // Form an array specifying which columns to return. 
                String[] projection = new String[] {
                        AccountColumns._ID
                };
                // Get the base URI for the Auth users table content provider.
                Uri authUsers =  AccountColumns.CONTENT_URI;

                // build the where clause
                String where = AccountColumns.EMAIL + "= ?";
                String[] whereParams = new String[]{userMail};  
                
                // Make the query. 
                Cursor managedCursor = mProvider.query(
                        authUsers,         // content_uri
                        projection,        // Which columns to return 
                        where,             // Which rows to return (email = ?)
                        whereParams,       // Selection arguments (new ActiveUser)
                        null);             // user order       

                //set values (will be used in a near future)                
                values.put(AccountColumns.EMAIL, userMail);
                values.put(AccountColumns.ACCESS_TOKEN, accessToken);
                values.put(AccountColumns.REFRESH_TOKEN, refreshToken);
                values.put(AccountColumns.ACTIVE_USER, true);
                values.put(AccountColumns.EXPIRATION_DATE,  mDateFormatter.format(expirationDate));
                
                // verify if user exists on dataBase
                if (managedCursor.moveToFirst() == true) {
                    Log.d(TAG, "User " + userMail + " already exists, updating data" );
                    
                    // user exists, update it!!                      
                    boolean updateResult = updateUser(userMail, values);
                    if (updateResult == true) {
                        Log.d(TAG, "User " + userMail + " updated and defined as ACTIVE");
                        userWasAdded = true;
                    }
                } else {
                    Log.d(TAG, "User " + userMail + " is a new user!" );

                    mProvider.insert(AccountColumns.CONTENT_URI, values);
                    Log.d(TAG, "User " + userMail + " inserted and defined as ACTIVE");
                    userWasAdded = true;                   
                }
                //always close a cursor, I said always
                managedCursor.close();
                
                //update current values?
                if (userWasAdded == true) {                    
                    mAccessToken = accessToken;
                    mExpirationDate = expirationDate;
                    mUserMail = userMail;
                    mRefreshToken = refreshToken;
                    //Later will be recovered
                    mUserId = null;
                }
            }
        } 
        
        
        // others users should be disabled
        // where clause
        String where = AccountColumns.EMAIL + " != ?";
        String[] whereParams = new String[]{mUserMail};
        values.clear();
        values.put(AccountColumns.ACTIVE_USER, false);
        
        // update the rows
        mProvider.update(AccountColumns.CONTENT_URI, values, where, whereParams);
        
        Log.d(TAG, "addActiveUserToken end (result = " + userWasAdded + ")");
        return userWasAdded;
    }
    
    /**
     * Returns a valid SessionManager
     * @param context app context
     * @return SessionManager instance
     */
    public static synchronized UserManager getInstance(Context context){        
        if (sInstance == null) {
            sInstance = new UserManager(context);
        }
        
        return sInstance;
    }
        
    /**
     * Get a valid Access Token
     * @return valid Access Token 
     * @throws IllegalStateException Throws this exception if there is no valid token
     */
    public String getActiveUserAccessToken()  throws IllegalStateException {
        
        synchronized (mUserMail) {
            if (mAccessToken == null) {
                throw new IllegalStateException("No token found!");
            } else {
                //try to get the freshest token and verify its result
                if (refreshToken() == false) {
                    throw new IllegalStateException("Problem to recover new valid token!");
                }        
            }
        }   
        //everything ok! return token
        return mAccessToken;
    }
    
    /**
     * Verify if the Session has a valid token to provide
     * @return true if token is available, false otherwise
     */
    public boolean hasUserActiveAccessToken() {
        synchronized (mUserMail) {
            return (mAccessToken != null);
        }
    }
    
    /**
     * active user id
     * @return active user id
     */
    public String getActiveUserId() {
        if(mUserId == null){
            recoverDataFromDataBase();
        }
        return mUserId;
    }
            
    /**
     * Change the active account
     * @param mail mail of new active account
     */
    public void changeAccountActive(String mail) {
        Log.d(TAG, "changeAccountActive begin");
        
        Log.d(TAG, "The new active user is " + mail);
        
        if (mUserMail != null && mUserMail.equalsIgnoreCase(mail)) {
            // Active user = new user
            // just leave
            Log.d(TAG, "The new active user is equal to the older one");
            return;
        }
        
        //values to be inserted/updated
        ContentValues values = new ContentValues();      
        
        String where = AccountColumns.EMAIL + " = ?";
        String[] whereParams = new String[]{mail};
        values.put(AccountColumns.ACTIVE_USER, true);
        
        // update the rows
        int rowsAffected = mProvider.update(AccountColumns.CONTENT_URI, values, where, whereParams);
        
        Log.d(TAG, rowsAffected + " line on database was passed to active");
                
        // others users should be disabled
        // where clause
        where = AccountColumns.EMAIL + " != ?";
        whereParams = new String[]{mail};
        values.clear();
        values.put(AccountColumns.ACTIVE_USER, false);
        
        // update the rows
        rowsAffected = mProvider.update(AccountColumns.CONTENT_URI, values, where, whereParams);

        Log.d(TAG, rowsAffected + " lines on database were passed to inactive");
        recoverDataFromDataBase();        
    }

    /**
     * Get the Email of the active account
     * @return email of the active account
     */
    public String getActiveUserEmail() {
        return mUserMail;
    }

    /**
     * Get a cursor containing all emails configurated on app
     * @return a cursor containing all emails configurated on app
     */
    public Cursor getAllAccountsEmail() {
       
        // Form an array specifying which columns to return. 
        String[] projection = new String[] {                
                AccountColumns.EMAIL
        };        
        // Make the query. 
        Cursor managedCursor = mProvider.query(
                AccountColumns.CONTENT_URI,      // uri                
                projection,                      // Which columns to return 
                null,                            // Which rows to return 
                null,                            // Selection arguments 
                AccountColumns.EMAIL + " ASC"   // Put the results in ascending order by email
        ); 
        return managedCursor;
    }

    /**
     * Delete an inactive account
     * @param account thath should be deleted
     */
    public boolean deleteInactiveAccount(String account) {
        
       if (account == null || account.equals(getActiveUserEmail())) {
           //trying to delete null account or active account
           return false;           
       } else {
           //delete user
           String where = AccountColumns.EMAIL + "=?";
           String[] whereParams = new String[]{account};        
           int result = mProvider.delete(AccountColumns.CONTENT_URI, where, whereParams);
           Log.d(TAG, "Result delete: " + result);           
           return (result == 1);           
       }       
    }
}
