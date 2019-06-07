/*
 * Copyright 2017 Kris_McGlinn.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tcd;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.tan;

import java.math.RoundingMode;
import java.text.DecimalFormat;


/**
 *
 * @author Kris_McGlinn - convertedf from - http://www.whoi.edu/marine/ndsf/utility/NDSFutility.js
 * most conversion functions were originally converted from C functions provided by 
   Dana Yoerger, Steve Gegg, and Louis Whitcomb at WHOI
 */
public class NDSFutility {
    
    double CMS_INCH = 2.54; 		/* cms/inch */
    double METERS_FOOT = 0.3048; 	/* meters/foot */
    double METERS_NMILE = 1852.0;       /* meters/nautical mile */
    double METERS_FATHOM = 1.8288;  	/* meters/fathom (straight conversion) */
    double UMETERS_UFATHOM = 1.8750; 	/* unc mtrs/unc fthm - accounts for */
                                        /* snd vel of 1500m/sec to 800fm/sec */
    double PI = 3.1415927;
    double TWO_PI = 6.2831854;  
    double RTOD =57.29577951308232; 
    double DTOR =(1.0/RTOD); 
    double RADIANS = 57.29577951; 	/* degrees/radian */
    int SECS_HOUR = 3600; 
    
    /* coordinate translation options */
    int XY_TO_LL= 1;
    int LL_TO_XY= 2;
    int LL_TO_LL= 3;

    /* coordinate system types  */
    int GPS_COORDS_WGS84 = 1;
    int GPS_COORDS_C1866 = 2;
    int LORANC_COORDS = 3;
    int SURVEY_COORDS = 4;
    int TRANSIT_COORDS = 5;

    double RADIUS = 6378137.0;
    double FLATTENING = 0.00335281068; /* GRS80 or WGS84 */
    double K_NOT = 0.9996;     /* UTM scale factor */
    double DEGREES_TO_RADIANS = 0.01745329252;
    double FALSE_EASTING = 500000.0;
    double FALSE_NORTHING = 10000000.0;
    
    public NDSFutility ()
    {
        
    }
    
    public double[] xy2ll(double lon, double lat, int coord_system, double x, double y, double xoffset_mtrs, double yoffset_mtrs, double rotation_angle_degs, double rms_error)
    {
        double origin[] = {x,y,coord_system,lat,lon,xoffset_mtrs,yoffset_mtrs,rotation_angle_degs,rms_error};
        double xy2ll[] = translate_coordinates(XY_TO_LL, origin);

       return xy2ll;
    }  
    
    public double DEG_TO_RADIANS(double x)
    { 
        return (x/RADIANS); 
    }
    
    public double METERS_DEGLON(double x)
    {  
    	// SEE https://stackoverflow.com/questions/639695/how-to-convert-latitude-or-longitude-to-meters
        double d2r=DEG_TO_RADIANS(x);
        return((111415.13 * cos(d2r))- (94.55 * cos(3.0*d2r)) + (0.12 * cos(5.0*d2r)));

    }

    public double METERS_DEGLAT(double x)
    {

        double d2r=DEG_TO_RADIANS(x);
        return(111132.09 - (566.05 * cos(2.0*d2r))+ (1.20 * cos(4.0*d2r)) - (0.002 * cos(6.0*d2r)));
    }
    
    /*----------------------------------------------------------
    #   The following functions are modified from the origin
    #   C functions created by Louis Whitcomb 19 Jun 2001
     ---------------------------------------------------------*/
    /*----------------------------------------------------------
    #   translate_coordinates
    #   routine to translate between geographic and cartesian coordinates
    #   user must supply following data on the cartesian coordinate system:
    #   location of the origin in lat/lon degrees;
    #   rotational skew from true north in degrees;
    #   N.B. sense of rotation i/p here is exactly as o/p by ORIGIN
    #   x/y offset in meters - only if an offset was used during the
    #   running of prog ORIGIN;
    */

    public double[] translate_coordinates(int trans_option, double[] d)
    {
    	//      double origin[] = {x,y,coord_system,lat,lon,xoffset_mtrs,yoffset_mtrs,rotation_angle_degs,rms_error};
        //      origin={slat:slat, slon:slon, coord_system:1, olat:olat, olon:olon, xoffset_mtrs:0,yoffset_mtrs:0,rotation_angle_degs:0,rms_error:0};
        double xx,yy,r,ct,st,angle;
        angle = DEG_TO_RADIANS(d[7]);
        double sll[] = new double[2];
        

        
        if( trans_option == XY_TO_LL)
        {
             /* X,Y to Lat/Lon Coordinate Translation  */

            xx = d[0] - d[5];
            yy = d[1] - d[6];
            r = sqrt(xx*xx + yy*yy);

//             if(r)
//             {
            ct = xx/r;
            st = yy/r;
            xx = r * ( (ct * cos(angle))+ (st * sin(angle)) );
            yy = r * ( (st * cos(angle))- (ct * sin(angle)) );
            
            double plon = d[4] + xx/METERS_DEGLON(d[2]);
            double plat = d[3] + yy/METERS_DEGLAT(d[2]);
                   
            sll[0] = plat;
            sll[1] = plon;
            
            //sll[0] = plat*.9999;
            //sll[1] = plon;
            //System.out.println("**plon = " + plon); 
            //System.out.println("**plat*.999 = " + plat*.999);

            
        }
//        else if(trans_option == LL_TO_XY)
//        {
//            xx = (porg.slon - porg.olon)*METERS_DEGLON(porg.olat);
//            yy = (porg.slat - porg.olat)*METERS_DEGLAT(porg.olat);
//
//            r = sqrt(xx*xx + yy*yy);
//
//            /* alert('LL_TO_XY: xx=' + xx + ' yy=' + yy + ' r=' + r);
//            return false;*/
//
//            if(r)
//            {
//                ct = xx/r;
//                st = yy/r;
//                xx = r * ( (ct * cos(angle)) + (st * sin(angle)) );
//                yy = r * ( (st * cos(angle)) - (ct * sin(angle)) );
//            }
//            pxpos_mtrs = xx + porg.xoffset_mtrs;
//            pypos_mtrs = yy + porg.yoffset_mtrs;
//
//            var sxy={};
//            sxy={x:pxpos_mtrs, y:pypos_mtrs};
//        
//        
//            return sxy;      
//        }
        return sll;
    }
        
//
    public double[] moveCoord(double lat, double lon, double offsetN, double offsetE)
    {

        //Coordinate offsets in radians
        double dLat = offsetN/RADIUS;
        double dLon = offsetE/(RADIUS*cos(PI*lat/180));

        //OffsetPosition, decimal degrees
        double latO = lat + dLat * 180/PI;
        double lonO = lon + dLon * 180/PI ;
        double ll[] = {latO, lonO};
        
        return ll;
    }
    
    
    public int utm_zone(double slat, double slon)
    {
//       with(Math)
//       {
          /* determine the zone for the given longitude 
             with 6 deg wide longitudinal strips */

          double zlon= slon + 180; /* set the lon from 0-360 */
          int i;

          for (i=1; i<=60; i++)
          { 
             if ( zlon >= (i-1)*6 & zlon < i*6)
             {
                break;
             }
          }
          int zone=i;

          /*  modify the zone number for special areas */
          if ( slat >=72 & (slon >=0 & slon <=36))
          {
              if (slon < 9.0)
              {
                  zone= 31;
              }
              else if ( slon  >= 9.0 & slon < 21)
              {
                  zone= 33;
              }
              else if ( slon >= 21.0 & slon < 33)
              {
                  zone= 35;
              }
              else if ( slon  >= 33.0 & slon < 42)
              {
                 zone= 37;
              }
          }
          if ( (slat >=56 & slat < 64) & (slon >=3 & slon < 12))
          {
              zone= 32;  /* extent to west ward for 3deg more */
          }
          return (zone);
//        }
//        return true;
    }
    
    public double[] geo_utm(double lat, double lon, int zone)
    {
//       with(Math)
//       {
          /* first compute the necessary geodetic parameters and constants */

          double lambda_not = ((-180.0 + zone*6.0) -3.0)/RADIANS ;
          double e_squared = 2.0 * FLATTENING - FLATTENING*FLATTENING;
          double e_fourth = e_squared * e_squared;
          double e_sixth = e_fourth * e_squared;
          double e_prime_sq = e_squared/(1.0 - e_squared);
          double sin_phi = sin(lat);
          double tan_phi = tan(lat);
          double cos_phi = cos(lat);
          double N = RADIUS/sqrt(1.0 - e_squared*sin_phi*sin_phi);
          double T = tan_phi*tan_phi;
          double C = e_prime_sq*cos_phi*cos_phi;
          double M = RADIUS*((1.0 - e_squared*0.25 -0.046875*e_fourth  -0.01953125*e_sixth)*lat-
                  (0.375*e_squared + 0.09375*e_fourth +
                                     0.043945313*e_sixth)*sin(2.0*lat) +
                  (0.05859375*e_fourth + 0.043945313*e_sixth)*sin(4.0*lat) -
                  (0.011393229 * e_sixth)*sin(6.0*lat));
          double A = (lon - lambda_not)*cos_phi;
          double A_sq = A*A;
          double A_fourth =  A_sq*A_sq;

          /* now go ahead and compute X and Y */

          double x_utm = K_NOT*N*(A + (1.0 - T + C)*A_sq*A/6.0 +
                       (5.0 - 18.0*T + T*T + 72.0*C - 
                        58.0*e_prime_sq)*A_fourth*A/120.0);

          /* note:  specific to UTM, vice general trasverse mercator.  
             since the origin is at the equator, M0, the M at phi_0, 
             always equals zero, and I won't compute it   */                                            

           double y_utm = K_NOT*(M + N*tan_phi*(A_sq/2.0 + 
                                (5.0 - T + 9.0*C + 4.0*C*C)*A_fourth/24.0 +
                                (61.0 - 58.0*T + T*T + 600.0*C - 
                                 330.0*e_prime_sq)*A_fourth*A_sq/720.0));

           /* now correct for false easting and northing */

           if( lat < 0)
           {
              y_utm +=10000000.0;
           }
           x_utm +=500000;

           /* adds Java function returns */
           double[] utmxy={x_utm,y_utm};
           return(utmxy);
//        }
//        return true;
    }

    
    public double[] xy2utm(double glat, double glong, double x, double y)
    {
        double[] coordinate = xy2ll(glat, glong, 1, x, y, 0, 0, 0, 0);
        
        double slat=coordinate[0];  /* decimal degrees to radius */
        double slon=coordinate[1]; 
        
        int utmzone=utm_zone(slat,slon); /* take slat and slon in degrees */

        double slat_rad=DTOR*slat;  /* decimal degrees to radius */
        double slon_rad=DTOR*slon; 
        
        double[] utmxy = geo_utm(slat_rad,slon_rad,utmzone);
        utmxy[0] = utmxy[0]/1000000;
        utmxy[1] = utmxy[1]/1000000;
        
        return utmxy;
    }

}