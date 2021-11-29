// Compile: gcc -rdynamic hellow.c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
void recurseDepth(int n)
{
	if(n<1)
	{
		malloc(3456);
		return;
	}else
	{
		recurseDepth(n-1);
	}
}
static void printn(char * str, int l)
{
	for(int i=0;i<l;++i)
	{
		char c=str[i];
		if(c<' ') c='.';
		printf("%c", c);
	}
}

int main ()
{
	printf("Hello World!\n");
	void * p=malloc(111);
	free(p);
	for(int i=0;i<20;++i)
	{
		recurseDepth(i);
	}
	p=malloc(112);
	p=realloc(p, 2000);
	free(p);
	char * str=(char *)malloc(100);
	malloc(200);	// str should not be the "last" block
	strcpy(str, "Example not freed - lklkhiygkytyfjrtdshtrstreshrsdrshtrsdtrstrsres");
	int l=strlen(str);
	printf("Before free: ", str);
	printn(str, l);
	printf("\n");
	free(str);
	printf("After free: ", str);
	printn(str, l);
	printf("\n");
}

