package com.dotcms.publisher.assets.business;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.dotcms.publisher.assets.bean.PushedAsset;
import com.dotcms.publisher.util.PublisherUtil;
import com.dotmarketing.business.CacheLocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.util.UtilMethods;

public class PushedAssetsFactoryImpl extends PushedAssetsFactory {
	private PushedAssetsCache cache=CacheLocator.getPushedAssetsCache();
	public void savePushedAsset(PushedAsset asset) throws DotDataException {
		final DotConnect db = new DotConnect();
		db.setSQL(INSERT_ASSETS);
		db.addParam(asset.getBundleId());
		db.addParam(asset.getAssetId());
		db.addParam(asset.getAssetType());
		db.addParam(asset.getPushDate());
		db.addParam(asset.getEnvironmentId());
		db.loadResult();
		cache.removePushedAssetById(asset.getAssetId(), asset.getEnvironmentId());
	}

	@Override
	public void deletePushedAssets(String bundleId, String environmentId)
			throws DotDataException {
		final DotConnect db = new DotConnect();
		db.setSQL(DELETE_ASSETS_BY_BUNDLE_ENV);
		db.addParam(bundleId);
		db.addParam(environmentId);
		db.loadResult();
		cache.clearCache();

	}

	@Override
	public void deletePushedAssets(String assetId)
			throws DotDataException {
		final DotConnect db = new DotConnect();
		db.setSQL(DELETE_ASSETS_BY_ASSET_ID);
		db.addParam(assetId);
		db.loadResult();
		cache.clearCache();
	}

	@Override
	public void deletePushedAssetsByEnvironment(String environmentId)
			throws DotDataException {
		final DotConnect db = new DotConnect();
		db.setSQL(DELETE_ASSETS_BY_ENVIRONMENT_ID);
		db.addParam(environmentId);
		db.loadResult();
		cache.clearCache();
	}

	@Override
	public List<PushedAsset> getPushedAssets(String bundleId, String environmentId)
			throws DotDataException {
		List<PushedAsset> assets = new ArrayList<PushedAsset>();

		if(!UtilMethods.isSet(bundleId) || !UtilMethods.isSet(environmentId)) {
			return assets;
		}

		DotConnect dc = new DotConnect();
		dc.setSQL(SELECT_ASSETS_BY_BUNDLE_ENV);
		dc.addParam(bundleId);
		dc.addParam(environmentId);

		List<Map<String, Object>> res = dc.loadObjectResults();

		for(Map<String, Object> row : res){
			PushedAsset asset = PublisherUtil.getPushedAssetByMap(row);
			assets.add(asset);
		}

		return assets;

	}



	@Override
	public void deleteAllPushedAssets() throws DotDataException {
		final DotConnect db = new DotConnect();
		db.setSQL(DELETE_ALL_ASSETS);
		db.loadResult();
		cache.clearCache();
	}

	@Override
	public List<PushedAsset> getPushedAssets(String assetId)
			throws DotDataException {
		List<PushedAsset> assets = new ArrayList<PushedAsset>();

		if(!UtilMethods.isSet(assetId)) {
			return assets;
		}

		DotConnect dc = new DotConnect();
		dc.setSQL(SELECT_ASSETS_BY_ASSET_ID);
		dc.addParam(assetId);

		List<Map<String, Object>> res = dc.loadObjectResults();

		for(Map<String, Object> row : res){
			PushedAsset asset = PublisherUtil.getPushedAssetByMap(row);
			assets.add(asset);
		}

		return assets;
	}

	@Override
	public List<PushedAsset> getPushedAssetsByEnvironment(String environmentId)
			throws DotDataException {
		List<PushedAsset> assets = new ArrayList<PushedAsset>();

		if(!UtilMethods.isSet(environmentId)) {
			return assets;
		}

		DotConnect dc = new DotConnect();
		dc.setSQL(SELECT_ASSETS_BY_ENV_ID);
		dc.addParam(environmentId);

		List<Map<String, Object>> res = dc.loadObjectResults();

		for(Map<String, Object> row : res){
			PushedAsset asset = PublisherUtil.getPushedAssetByMap(row);
			assets.add(asset);
		}

		return assets;
	}
	
	
	public PushedAsset getLastPushForAsset(String assetId, String environmentId)  throws DotDataException{
		
		PushedAsset asset = cache.getPushedAsset(assetId, environmentId);
		if(asset==null){
			DotConnect dc = new DotConnect();
			dc.setSQL(SELECT_ASSET_LAST_PUSHED);
			dc.addParam(assetId);
			dc.addParam(environmentId);
			dc.setMaxRows(1);
			List<Map<String, Object>> res = dc.loadObjectResults();
	
			for(Map<String, Object> row : res){
				asset = PublisherUtil.getPushedAssetByMap(row);
				cache.add(asset);
			}
		}
		
		return asset;
		
		
	}

}
