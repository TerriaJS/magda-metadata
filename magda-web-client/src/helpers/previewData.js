// @flow
import type {ParsedDistribution} from '../helpers/record';

export type PreviewData = {
  data: Array<any> | string,
  meta: {
    type: string,
    filed? : Array<string>
  }
}

export function getPreviewDataUrl(distribution: ParsedDistribution){

    if((!distribution.linkStatusAvailable || (distribution.linkStatusAvailable && distribution.linkActive)) && (distribution.downloadURL || distribution.accessURL)){
      // try to get preview data
      const format  = distribution.format.toLowerCase();
      const geoFormat = ["csv-geo-au" , "wfs" , "wms" , "czml" , "kml"];
      const normaFormat = ['csv', 'xml', 'json', 'txt', 'html', 'rss' ];
      const chartingFormat = ['csv'];
      if(geoFormat.indexOf(distribution.format) !== -1){
        return {id: distribution.id , format: 'geo', name: distribution.title}
      } else if((chartingFormat.indexOf(distribution.format) !== -1) && distribution.isTimeSeries === true){
        return {url: distribution.downloadURL, format: 'chart'}
      }else if(normaFormat.indexOf(distribution.format) !== -1){
        return {url: distribution.downloadURL || distribution.accessURL, format: distribution.format.toLowerCase()}
      }
      return {url: distribution.downloadURL, format: 'googleViewable'}
    }
    return false;

  }
