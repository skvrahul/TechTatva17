package in.tt.tt17.fragments;


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import in.tt.tt17.R;
import in.tt.tt17.activities.MainActivity;
import in.tt.tt17.adapters.HomeAdapter;
import in.tt.tt17.adapters.HomeCategoriesAdapter;
import in.tt.tt17.adapters.HomeEventsAdapter;
import in.tt.tt17.adapters.HomeResultsAdapter;
import in.tt.tt17.models.categories.CategoryModel;
import in.tt.tt17.models.events.ScheduleModel;
import in.tt.tt17.models.favourites.FavouritesModel;
import in.tt.tt17.models.instagram.InstagramFeed;
import in.tt.tt17.models.result.EventResultModel;
import in.tt.tt17.models.result.ResultModel;
import in.tt.tt17.models.result.ResultsListModel;
import in.tt.tt17.network.APIClient;
import in.tt.tt17.network.InstaFeedAPIClient;
import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class HomeFragment extends Fragment {
    private InstagramFeed feed;
    SwipeRefreshLayout swipeRefreshLayout;
    private HomeAdapter instaAdapter;
    private HomeResultsAdapter resultsAdapter;
    private HomeCategoriesAdapter categoriesAdapter;
    private HomeEventsAdapter eventsAdapter;
    private RecyclerView homeRV;
    private RecyclerView resultsRV;
    private RecyclerView  categoriesRV;
    private RecyclerView eventsRV;
    private TextView resultsMore;
    private TextView categoriesMore;
    private TextView eventsMore;
    private TextView resultsNone;
    private CardView homeResultsItem;
    private ProgressBar progressBar;
    private BottomNavigationView navigation;
    private AppBarLayout appBarLayout;
    private TextView instaTextView;
    private boolean initialLoad = true;
    private int processes = 0;
    String TAG = "HomeFragment";
    Realm mDatabase = Realm.getDefaultInstance();
    private List<EventResultModel> resultsList = new ArrayList<>();
    private List<CategoryModel> categoriesList = new ArrayList<>();
    private List<ScheduleModel> eventsList = new ArrayList<>();
    public HomeFragment() {
    }

    public static HomeFragment newInstance() {
        HomeFragment fragment = new HomeFragment();
        return fragment;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTitle(R.string.fest_name);
        fetchResults();

    }
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = initViews(inflater, container);

        progressBar = (ProgressBar)view.findViewById(R.id.insta_progress);
        instaTextView = (TextView)view.findViewById(R.id.insta_text_view);

        displayInstaFeed();

        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        resultsAdapter = new HomeResultsAdapter(resultsList,getActivity());
        resultsRV.setAdapter(resultsAdapter);
        resultsRV.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL,false));
        resultsMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //MORE Clicked - Take user to Results Fragment
                Log.i(TAG, "onClick: Results more");
                ((MainActivity)getActivity()).changeFragment(ResultsFragment.newInstance());
            }
        });
        //Display Categories
        RealmResults<CategoryModel> categoriesRealmList = mDatabase.where(CategoryModel.class).findAllSorted("categoryName");
        categoriesList = mDatabase.copyFromRealm(categoriesRealmList);
        if(categoriesList.size()>10){
            categoriesList.subList(10,categoriesList.size()).clear();
        }
        categoriesAdapter = new HomeCategoriesAdapter(categoriesList,getActivity());
        categoriesRV.setAdapter(categoriesAdapter);
        categoriesRV.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL,false));
        categoriesAdapter.notifyDataSetChanged();
        categoriesMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //MORE Clicked - Take user to Categories Fragment
                Log.i(TAG, "onClick: Categories More");
                ((MainActivity)getActivity()).changeFragment(CategoriesFragment.newInstance());

            }
        });
        if(categoriesList.size()==0){
            view.findViewById(R.id.home_categories_none_text_view).setVisibility(View.VISIBLE);
        }

        //Display Events of current day
        Calendar cal = Calendar.getInstance();
        Calendar day2 = new GregorianCalendar(2017, 9, 5);
        Calendar day3 = new GregorianCalendar(2017, 9, 6);
        Calendar day4 = new GregorianCalendar(2017, 9, 7);
        Calendar curDay = new GregorianCalendar(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        int dayOfEvent;

        if (curDay.getTimeInMillis() < day2.getTimeInMillis()){
            dayOfEvent = 1;
        }else if (curDay.getTimeInMillis() < day3.getTimeInMillis()){
            dayOfEvent = 2;
        }else if (curDay.getTimeInMillis() < day4.getTimeInMillis()){
            dayOfEvent = 3;
        }else {
            dayOfEvent = 4;
        }

        String sortCriteria[] = {"day", "startTime", "eventName"};
        Sort sortOrder[] = {Sort.ASCENDING, Sort.ASCENDING, Sort.ASCENDING};

        RealmResults<ScheduleModel> eventsRealmResults = mDatabase.where(ScheduleModel.class).equalTo("day", dayOfEvent+"").findAllSorted(sortCriteria, sortOrder);

        eventsList = mDatabase.copyFromRealm(eventsRealmResults);
        for(int i=0;i<eventsList.size();i++){
            ScheduleModel event = eventsList.get(i);
            if(isFavourite(event)){
                //Move to top if the event is a Favourite
                eventsList.remove(event);
                eventsList.add(0, event);
            }
        }
        if(eventsList.size()>10){
            eventsList.subList(10, eventsList.size()).clear();
        }
        eventsAdapter = new HomeEventsAdapter(eventsList, null,getActivity());
        Log.i(TAG, "onCreateView: eventsList size"+eventsList.size());
        eventsRV.setAdapter(eventsAdapter);
        eventsRV.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL,false));
        eventsAdapter.notifyDataSetChanged();
        eventsMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //MORE Clicked - Take user to Events Fragment
                Log.i(TAG, "onClick: Events More");
                ((MainActivity)getActivity()).changeFragment(EventsFragment.newInstance());
            }
        });
        if(eventsList.size()==0){
            view.findViewById(R.id.home_events_none_text_view).setVisibility(View.VISIBLE);
        }
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                ConnectivityManager cmTemp = (ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkTemp = cmTemp.getActiveNetworkInfo();
                boolean isConnectedTemp = activeNetworkTemp != null && activeNetworkTemp.isConnectedOrConnecting();
                if(isConnectedTemp){
                    displayInstaFeed();
                    fetchResults();
                    new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                        }, 5000);
                }
                else{
                    Snackbar.make(view, "Check connection!", Snackbar.LENGTH_SHORT).show();
                    swipeRefreshLayout.setRefreshing(false);}

            }
        });
        return view;
    }
    public void displayInstaFeed(){
        if (initialLoad) progressBar.setVisibility(View.VISIBLE);
        homeRV.setVisibility(View.GONE);
        instaTextView.setVisibility(View.GONE);
        Call<InstagramFeed> call = InstaFeedAPIClient.getInterface().getInstagramFeed();
        processes ++;
        call.enqueue(new Callback<InstagramFeed>() {
            @Override
            public void onResponse(Call<InstagramFeed> call, Response<InstagramFeed> response) {
                if (initialLoad) progressBar.setVisibility(View.GONE);
                if(response.isSuccess()){
                    feed = response.body();
                    instaAdapter =  new HomeAdapter(feed);
                    homeRV.setAdapter(instaAdapter);
                    homeRV.setLayoutManager(new LinearLayoutManager(getContext()));
                    ViewCompat.setNestedScrollingEnabled(homeRV, false);
                }
                homeRV.setVisibility(View.VISIBLE);
                initialLoad = false;
            }

            @Override
            public void onFailure(Call<InstagramFeed> call, Throwable t) {
                if (initialLoad) progressBar.setVisibility(View.GONE);
                instaTextView.setVisibility(View.VISIBLE);
                Log.i(TAG, "onFailure: Error Fetching insta feed ");
                initialLoad = false;
            }
        });
    }
    public void updateResultsList(){
        RealmResults<ResultModel> results = mDatabase.where(ResultModel.class).findAllSorted("eventName", Sort.ASCENDING, "position",Sort.ASCENDING );
        if (!results.isEmpty()){
            resultsList.clear();
            List<String> eventNamesList = new ArrayList<>();
            for (ResultModel result : results){
                String eventName = result.getEventName()+" "+result.getRound();
                if (eventNamesList.contains(eventName)){
                    resultsList.get(eventNamesList.indexOf(eventName)).eventResultsList.add(result);
                }
                else{
                    EventResultModel eventResult = new EventResultModel();
                    eventResult.eventName = result.getEventName();
                    eventResult.eventRound = result.getRound();
                    eventResult.eventCategory = result.getCatName();
                    eventResult.eventResultsList.add(result);
                    resultsList.add(eventResult);
                    eventNamesList.add(eventName);
                }
            }
        }
        Log.i(TAG, "displayResults: resultsList size:"+resultsList.size());
        //Moving favourite results to top
        for(EventResultModel result: resultsList){
            if(isFavourite(result)){
                resultsList.remove(result);
                resultsList.add(0, result);
            }
        }
        //Picking first 10 results to display
        if(resultsList.size()>10){
            resultsList.subList(10,resultsList.size()).clear();
        }
        resultsAdapter.notifyDataSetChanged();

        if(resultsList.size()==0){
            resultsNone.setVisibility(View.VISIBLE);
        }
    }

    public void fetchResults(){
        processes++;
        Call<ResultsListModel> callResultsList = APIClient.getAPIInterface().getResultsList();
        callResultsList.enqueue(new Callback<ResultsListModel>() {
            List<ResultModel> results = new ArrayList<>();
            @Override
            public void onResponse(Call<ResultsListModel> call, Response<ResultsListModel> response) {
                if (response.isSuccess() && response.body() != null){
                    results = response.body().getData();
                    mDatabase.beginTransaction();
                    mDatabase.where(ResultModel.class).findAll().deleteAllFromRealm();
                    mDatabase.copyToRealm(results);
                    mDatabase.commitTransaction();
                    homeResultsItem.setVisibility(View.VISIBLE);
                    updateResultsList();
                    resultsNone.setVisibility(View.GONE);
                    resultsNone.setText("");
                }
            }
            @Override
            public void onFailure(Call<ResultsListModel> call, Throwable t) {
                if(homeResultsItem.getVisibility()==View.VISIBLE)
                homeResultsItem.setVisibility(View.GONE);
                processes--;
            }
        });
    }
    public boolean isFavourite(ScheduleModel event){
        RealmResults<FavouritesModel> favouritesRealmList = mDatabase.where(FavouritesModel.class).equalTo("id",event.getEventID()).contains("day", event.getDay()).findAll();
        return (favouritesRealmList.size()!=0);
    }
    public boolean isFavourite(EventResultModel result){
        RealmResults<FavouritesModel> favouritesRealmList = mDatabase.where(FavouritesModel.class).equalTo("eventName",result.eventName).findAll();
        return (favouritesRealmList.size()!=0);
    }

    public View initViews(LayoutInflater inflater, ViewGroup container){
        appBarLayout = (AppBarLayout) container.findViewById(R.id.app_bar);
        navigation = (BottomNavigationView) container.findViewById(R.id.bottom_nav);
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        homeRV = (RecyclerView) view.findViewById(R.id.home_recycler_view);
        resultsRV = (RecyclerView) view.findViewById(R.id.home_results_recycler_view);
        categoriesRV = (RecyclerView) view.findViewById(R.id.home_categories_recycler_view);
        eventsRV = (RecyclerView) view.findViewById(R.id.home_events_recycler_view);
        resultsMore = (TextView) view.findViewById(R.id.home_results_more_text_view);
        categoriesMore = (TextView) view.findViewById(R.id.home_categories_more_text_view);
        eventsMore = (TextView) view.findViewById(R.id.home_events_more_text_view);
        resultsNone = (TextView) view.findViewById(R.id.home_results_none_text_view);
        homeResultsItem=(CardView) view.findViewById(R.id.home_results_item);
        instaTextView = (TextView) view.findViewById(R.id.instagram_textview);
        swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.home_swipe_refresh_layout);
        return view;
    }
}
