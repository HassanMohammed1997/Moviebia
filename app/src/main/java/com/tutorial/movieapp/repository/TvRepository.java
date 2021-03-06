package com.tutorial.movieapp.repository;

import com.tutorial.movieapp.local.dao.TvDao;
import com.tutorial.movieapp.local.entity.TvEntity;
import com.tutorial.movieapp.remote.NetworkBoundResource;
import com.tutorial.movieapp.remote.Resource;
import com.tutorial.movieapp.remote.TvApiService;
import com.tutorial.movieapp.remote.model.TvApiResponse;
import com.tutorial.movieapp.utils.AppUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Singleton;

import io.reactivex.Flowable;
import io.reactivex.Observable;

@Singleton
public class TvRepository
{
    private static final String TAG = "TvRepository";
    private TvApiService tvApiService;
    private TvDao tvDao;


    public TvRepository(TvApiService tvApiService, TvDao tvDao)
    {
        this.tvApiService = tvApiService;
        this.tvDao = tvDao;
    }

    public Observable<Resource<List<TvEntity>>> getTvListByType(Long page, String type)
    {
        return new NetworkBoundResource<List<TvEntity>, TvApiResponse>()
        {
            @Override
            protected void saveCallResult(TvApiResponse item)
            {
                List<TvEntity> tvEntities = new ArrayList<>();
                for (TvEntity tvEntity : item.getResults())
                {
                    TvEntity storedTvEntity = tvDao.getTvById(tvEntity.getId());
                    if (storedTvEntity == null)
                    {
                        tvEntity.setCategoryTypes(Collections.singletonList(type));
                    } else
                    {
                        List<String> categories = storedTvEntity.getCategoryTypes();
                        categories.add(type);
                        tvEntity.setCategoryTypes(categories);
                    }

                    tvEntity.setPage(item.getPage());
                    tvEntity.setTotalPages(item.getTotalPages());
                    tvEntities.add(tvEntity);
                }

                tvDao.insertAll(tvEntities);
            }

            @Override
            protected boolean shouldFetch()
            {
                return true;
            }

            @Override
            protected Flowable<List<TvEntity>> loadFromDb()
            {
                List<TvEntity> tvEntities = tvDao.getTvsByPage(page);
                if (tvEntities == null || tvEntities.isEmpty())
                {
                    return Flowable.empty();
                }
                return Flowable.just(AppUtils.getTvListByType(type, tvEntities));
            }

            @Override
            protected Observable<Resource<TvApiResponse>> createCall()
            {
                return tvApiService.fetchTvListByType(type, page)
                        .flatMap(response -> Observable.just(response == null
                                ? Resource.error("No data found", new TvApiResponse())
                                : Resource.success(response)));
            }
        }.getAsObservable();
    }

    public Observable<Resource<TvEntity>> getTvMovieDetails(Long movieId)
    {
        return new NetworkBoundResource<TvEntity, TvEntity>()
        {
            @Override
            protected void saveCallResult(TvEntity item)
            {
                TvEntity storedTvEntity = tvDao.getTvById(item.getId());
                if (storedTvEntity == null) tvDao.insert(item);
                else tvDao.update(item);
            }

            @Override
            protected boolean shouldFetch()
            {
                return true;
            }

            @Override
            protected Flowable<TvEntity> loadFromDb()
            {
                TvEntity tvMovie = tvDao.getTvById(movieId);
                if (tvMovie == null) return Flowable.empty();

                return Flowable.just(tvMovie);
            }

            @Override
            protected Observable<Resource<TvEntity>> createCall()
            {
                String id = String.valueOf(movieId);
                return Observable.combineLatest(
                        tvApiService.fetchTvDetail(id),
                        tvApiService.fetchTvReviews(id),
                        tvApiService.fetchTvVideo(id),
                        tvApiService.fetchSimilarTvList(id, 1),
                        tvApiService.fetchCastDetail(id),
                        (tvEntity, reviewResponse, videoResponse, tvResponse, creditResponse) ->
                        {
                            tvEntity.setReviews(reviewResponse.getResults());
                            tvEntity.setCrews(creditResponse.getCrew());
                            tvEntity.setCasts(creditResponse.getCast());
                            tvEntity.setVideos(videoResponse.getResults());
                            tvEntity.setSimilarTvEntities(tvResponse.getResults());
                            return Resource.success(tvEntity);
                        }
                );
            }
        }.getAsObservable();
    }

    public Observable<Resource<List<TvEntity>>> searchForMovie(String query, Long page)
    {
        return new NetworkBoundResource<List<TvEntity>, TvApiResponse>()
        {
            @Override
            protected void saveCallResult(TvApiResponse item)
            {
                List<TvEntity> tvEntities = new ArrayList<>();
                for (TvEntity tvEntity : item.getResults())
                {
                    TvEntity storedEntity = tvDao.getTvById(tvEntity.getId());
                    if (storedEntity == null)
                        tvEntity.setCategoryTypes(Collections.singletonList(query));
                    else
                    {
                        List<String> categories = storedEntity.getCategoryTypes();
                        categories.add(query);
                        tvEntity.setCategoryTypes(categories);
                    }

                    tvEntity.setPage(item.getPage());
                    tvEntity.setTotalPages(item.getTotalPages());
                    tvEntities.add(tvEntity);
                }

                tvDao.insertAll(tvEntities);
            }

            @Override
            protected boolean shouldFetch()
            {
                return true;
            }

            @Override
            protected Flowable<List<TvEntity>> loadFromDb()
            {
                List<TvEntity> tvEntities = tvDao.getTvsByPage(page);
                if (tvEntities == null || tvEntities.isEmpty())
                    return Flowable.empty();
                return Flowable.just(AppUtils.getTvListByType(query, tvEntities));
            }

            @Override
            protected Observable<Resource<TvApiResponse>> createCall()
            {
                return tvApiService.searchTvsByQuery(query, page)
                        .flatMap(response -> Observable.just(response == null
                                ? Resource.error("No data found", new TvApiResponse())
                                : Resource.success(response)));
            }
        }.getAsObservable();
    }
}
