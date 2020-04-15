#ifndef _METEC_FLAT20_IOCTL_H
#define _METEC_FLAT20_IOCTL_H

#define MAX_BRAILLE_LINE_SIZE 20

#define METEC_FLAT20_IOC_MAGIC 0xE2

#define UOUT1_ENABLE (1 << 4)
#define UOUT2_ENABLE (1 << 5)
#define UOUT3_ENABLE (1 << 7)

#define METEC_FLAT20_GET_DRIVER_VERSION \
  _IOC(_IOC_READ, METEC_FLAT20_IOC_MAGIC, 0x01, 4)
#define METEC_FLAT20_DISPLAY_CONTROL \
  _IOC(_IOC_READ, METEC_FLAT20_IOC_MAGIC, 0x02, 4)
/* {PK} parameters for METEC_FLAT20_DISPLAY_CONTROL ioctl */
#define DISPLAY_ENABLE 1
#define DISPLAY_DISABLE 0
#define METEC_FLAT20_CLEAR_DISPLAY \
  _IOC(_IOC_WRITE, METEC_FLAT20_IOC_MAGIC, 0x03, 4)
#define METEC_FLAT20_DISPLAY_WRITE \
  _IOC(_IOC_WRITE, METEC_FLAT20_IOC_MAGIC, 0x04, 4)
#define METEC_FLAT20_SET_DOT_STRENGTH \
  _IOC(_IOC_WRITE, METEC_FLAT20_IOC_MAGIC, 0x05, 4)
/* {PK} Dot Strength Values for METEC_FLAT20_SET_DOT_STRENGTH ioctl */
#define UOUT_155V_CONFIG_VALUE 0
#define UOUT_162V_CONFIG_VALUE (UOUT1_ENABLE)
#define UOUT_168V_CONFIG_VALUE (UOUT2_ENABLE)
#define UOUT_174V_CONFIG_VALUE (UOUT2_ENABLE | UOUT1_ENABLE)
#define UOUT_177V_CONFIG_VALUE (UOUT3_ENABLE)
#define UOUT_184V_CONFIG_VALUE (UOUT3_ENABLE | UOUT1_ENABLE)
#define UOUT_191V_CONFIG_VALUE (UOUT3_ENABLE | UOUT2_ENABLE)
#define UOUT_199V_CONFIG_VALUE (UOUT3_ENABLE | UOUT2_ENABLE | UOUT1_ENABLE)

#define METEC_FLAT20_IOC_MAXNR 0x05

#endif /* _METEC_FLAT20_IOCTL_H */